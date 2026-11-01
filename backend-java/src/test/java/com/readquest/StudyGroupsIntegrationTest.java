package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyGroupsIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      p.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:groups;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired StudyGroupService groups;
  @Autowired AccountService accounts;
  @Autowired GroupCoachService coach;
  @MockitoBean AiProvider provider;

  record Client(String id, String token) {}

  Client owner, alice, bob;
  String book;

  Client account() throws Exception {
    var result =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "displayName",
                                "Group Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    return new Client(auth.path("user").path("id").asText(), auth.path("accessToken").asText());
  }

  @BeforeEach
  void setup() throws Exception {
    owner = account();
    alice = account();
    bob = account();
    book = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,asset_scope,created_at,updated_at)"
            + " VALUES(?,?,'Group Book','completed','private',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        book,
        owner.id());
    db.update(
        "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content) VALUES(?,?,0,'One','We"
            + " watched the river.')",
        book,
        book);
  }

  void joined() {
    groups.join(alice.id(), book);
    groups.join(bob.id(), book);
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> send(Client user, String body, String client) {
    return (Map<String, Object>) groups.send(user.id(), book, body, client).get("message");
  }

  String id(Map<String, Object> message) {
    return message.get("id").toString();
  }

  @Test
  void explicitJoinIsIdempotentAndEarliestMemberReceivesAdmin() {
    assertEquals(false, groups.summary(alice.id(), book).get("joined"));
    assertEquals(true, groups.join(alice.id(), book).get("created"));
    assertEquals(false, groups.join(alice.id(), book).get("created"));
    groups.join(bob.id(), book);
    assertEquals("admin", groups.member(alice.id(), book, alice.id()).get("role"));
    groups.leave(alice.id(), book);
    assertEquals("admin", groups.member(bob.id(), book, bob.id()).get("role"));
    assertEquals(1L, groups.summary(bob.id(), book).get("memberCount"));
  }

  @Test
  void uploaderExplicitlyJoiningCanRecoverAdministratorAccess() {
    joined();
    groups.join(owner.id(), book);
    assertEquals("admin", groups.member(owner.id(), book, owner.id()).get("role"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () ->
            groups.removeMember(
                owner.id(), book, alice.id(), "Cannot remove another administrator"));
  }

  @Test
  void messageIdsRemainOrderedAndRetriesCannotDuplicateOrChangeContent() {
    joined();
    String client = UUID.randomUUID().toString();
    var first = send(alice, "We watched the river.", client);
    assertTrue(id(first).matches("[0-9a-f]{24}"));
    assertEquals(
        false, groups.send(alice.id(), book, "We watched the river.", client).get("created"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> groups.send(alice.id(), book, "We read a different story.", client));
    var second = send(bob, "The children waited.", UUID.randomUUID().toString());
    var third = send(alice, "The train arrived.", UUID.randomUUID().toString());
    assertTrue(id(first).compareTo(id(second)) < 0);
    assertTrue(id(second).compareTo(id(third)) < 0);
    var last = groups.messages(alice.id(), book, null, null, 1);
    assertEquals(id(third), ((Map<?, ?>) ((List<?>) last.get("messages")).getFirst()).get("id"));
    var after = groups.messages(alice.id(), book, null, id(first), 1);
    assertEquals(id(second), ((Map<?, ?>) ((List<?>) after.get("messages")).getFirst()).get("id"));
    assertEquals(true, ((Map<?, ?>) after.get("pageInfo")).get("hasMore"));
  }

  @Test
  void blockedMessagesAreAbsentFromHistoryAndEventFeedStillAdvances() {
    joined();
    groups.block(alice.id(), book, bob.id());
    String baseline = groups.events(alice.id(), book, null).get("cursor").toString();
    send(bob, "I think this scene is important.", UUID.randomUUID().toString());
    assertTrue(
        ((List<?>) groups.messages(alice.id(), book, null, null, 50).get("messages")).isEmpty());
    var feed = groups.events(alice.id(), book, baseline);
    assertTrue(((List<?>) feed.get("events")).isEmpty());
    assertNotEquals(baseline, feed.get("cursor"));
    groups.unblock(alice.id(), bob.id());
    assertEquals(
        1, ((List<?>) groups.messages(alice.id(), book, null, null, 50).get("messages")).size());
  }

  @Test
  void moderatorHierarchyMuteAndMessageDeletionAreAudited() {
    joined();
    groups.moderate(
        alice.id(),
        book,
        bob.id(),
        "moderator",
        Instant.now().plusSeconds(600),
        true,
        "Pause this discussion");
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> groups.send(bob.id(), book, "I agree with that.", UUID.randomUUID().toString()));
    groups.moderate(alice.id(), book, bob.id(), null, null, true, "Mute is no longer needed");
    var message = send(bob, "I agree with that.", UUID.randomUUID().toString());
    assertThrows(
        com.readquest.exception.ApiException.class,
        () ->
            groups.moderate(
                bob.id(),
                book,
                alice.id(),
                null,
                Instant.now().plusSeconds(600),
                true,
                "Forbidden"));
    groups.deleteMessage(alice.id(), book, id(message), "Remove duplicate discussion");
    var records = (List<?>) groups.messages(bob.id(), book, null, null, 50).get("messages");
    assertEquals("", ((Map<?, ?>) records.getFirst()).get("body"));
    assertEquals(true, ((Map<?, ?>) records.getFirst()).get("deleted"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM group_audit_events WHERE book_id=? AND action='message.deleted'",
            Integer.class,
            book));
  }

  @Test
  void rejectsForeignLanguageReportsAreScopedAndRemovalRevokesEventAccess() {
    joined();
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> groups.send(bob.id(), book, "我想讨论这一段。", UUID.randomUUID().toString()));
    var message = send(bob, "Why did the train stop?", UUID.randomUUID().toString());
    var report =
        groups.report(alice.id(), book, "message", id(message), null, "spam", "Repeated content");
    assertEquals("open", report.get("status"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> groups.report(bob.id(), book, "message", id(message), null, "spam", "Self report"));
    groups.removeMember(alice.id(), book, bob.id(), "Repeated spam");
    assertThrows(
        com.readquest.exception.ApiException.class, () -> groups.events(bob.id(), book, "0"));
  }

  @Test
  void accountDeletionTransfersAdministrationAndAnonymizesRetainedMessages() {
    joined();
    send(alice, "I like this part.", UUID.randomUUID().toString());
    String before = groups.events(bob.id(), book, null).get("cursor").toString();
    assertEquals("completed", accounts.requestDeletion(alice.id(), "Password123!").get("status"));
    assertEquals("admin", groups.member(bob.id(), book, bob.id()).get("role"));
    var messages = (List<?>) groups.messages(bob.id(), book, null, null, 50).get("messages");
    var sender = (Map<?, ?>) ((Map<?, ?>) messages.getFirst()).get("sender");
    assertEquals("", sender.get("userId"));
    assertEquals("Former member", sender.get("displayName"));
    assertFalse(((List<?>) groups.events(bob.id(), book, before).get("events")).isEmpty());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM group_audit_events WHERE actor_user_id=?",
            Integer.class,
            alice.id()));
  }

  @Test
  void englishCoachPreservesLocalFallbackAndRejectsInventedCorrections() throws Exception {
    joined();
    db.update(
        "INSERT INTO user_entitlements(id,source_order_no,user_id,code,label,starts_at)"
            + " VALUES(?,?,?,'pro','Test',CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        bob.id());
    when(provider.configured()).thenReturn(false);
    var fallback = coach.assist(bob.id(), book, "check", "I very like this book.", "B1");
    assertTrue(fallback.path("degraded").asBoolean());
    assertEquals("I really like this book.", fallback.path("corrected").asText());
    when(provider.configured()).thenReturn(true);
    when(provider.json(anyString(), anyString()))
        .thenReturn(
            json.readTree(
                "{\"isCorrect\":true,\"corrected\":\"A different sentence.\",\"issues\":[]}"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> coach.assist(bob.id(), book, "check", "I like this book.", "B1"));
  }
}
