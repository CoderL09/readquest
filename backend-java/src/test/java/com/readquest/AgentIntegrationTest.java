package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.dto.AgentRequest;
import com.readquest.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentIntegrationTest {
  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry props) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      props.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:agent;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AgentService service;
  @MockitoBean ToolModel model;
  @MockitoBean BookRetrievalService retrieval;
  String user, token, book;

  @BeforeEach
  void setup() throws Exception {
    var auth =
        json.readTree(
            mvc.perform(
                    post("/api/users")
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "email",
                                    UUID.randomUUID() + "@example.test",
                                    "displayName",
                                    "Agent test",
                                    "password",
                                    "Password123!"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    user = auth.path("user").path("id").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    book = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,visibility,asset_scope,created_at,updated_at) VALUES(?,?,?,'completed','public','private',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        book,
        user,
        "Agent test book");
    db.update(
        "INSERT INTO user_entitlements(id,user_id,code,label,source_order_no,starts_at,ends_at) VALUES(?,?,'pro','Test',?,CURRENT_TIMESTAMP(3),?)",
        UUID.randomUUID().toString(),
        user,
        UUID.randomUUID().toString(),
        java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)));
    when(retrieval.retrieve(eq(user), eq(book), anyString(), isNull(), eq(false)))
        .thenReturn(new BookRetrievalService.Result(List.of(), "lexical"));
  }

  JsonNode tool(String name, String args) {
    return json.valueToTree(
        Map.of(
            "role",
            "assistant",
            "tool_calls",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "type",
                    "function",
                    "function",
                    Map.of("name", name, "arguments", args)))));
  }

  JsonNode answer() {
    return json.valueToTree(Map.of("role", "assistant", "content", "先复习已有词汇，再回答阅读问题。"));
  }

  AgentRequest input() {
    return new AgentRequest(book, null, "帮我安排阅读复习", 5);
  }

  @Test
  void toolsAreNativeBoundToUserAndConfirmationIsIdempotent() throws Exception {
    when(model.turn(anyList(), anyList()))
        .thenReturn(
            tool("getReadingProgress", "{}"),
            tool("getReviewWords", "{}"),
            tool("searchBookPassages", "{\"query\":\"recent chapter\"}"),
            answer());
    var run = service.create(user, "agent-test-key", input());
    assertEquals("draft", run.get("state"));
    String id = (String) run.get("id");
    assertEquals(id, service.create(user, "agent-test-key", input()).get("id"));
    assertEquals(id, service.list(user).getFirst().get("id"));
    assertTrue(service.list("other-user").isEmpty());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
    var once = service.confirm(user, id);
    var twice = service.confirm(user, id);
    assertEquals("confirmed", once.get("state"));
    assertEquals(once.get("session"), twice.get("session"));
    verify(model, times(4)).turn(anyList(), anyList());
    verify(retrieval).retrieve(user, book, "recent chapter", null, false);
    mvc.perform(get("/api/agent-runs/" + id)).andExpect(status().isUnauthorized());
    assertThrows(RuntimeException.class, () -> service.get("other-user", id));
    assertThrows(
        RuntimeException.class,
        () ->
            service.create(
                user, "agent-test-key", new AgentRequest(book, null, "changed goal", 5)));
  }

  @Test
  void modelCannotInjectAnotherUserOrInvokeUnknownTools() {
    when(model.turn(anyList(), anyList()))
        .thenReturn(tool("getReviewWords", "{\"userId\":\"victim\"}"));
    assertThrows(RuntimeException.class, () -> service.create(user, "bad-tool-args", input()));
    assertEquals(
        "failed",
        db.queryForObject("SELECT state FROM agent_runs WHERE user_id=?", String.class, user));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM feature_usage WHERE user_id=? AND result='allowed'",
            Integer.class,
            user));
    when(model.turn(anyList(), anyList())).thenReturn(tool("deleteAccount", "{}"));
    assertThrows(RuntimeException.class, () -> service.create(user, "unknown-tool-key", input()));
  }

  @Test
  void rejectedArgumentsCanBeCorrectedWithinTheExistingToolBudget() {
    when(model.turn(anyList(), anyList()))
        .thenReturn(
            tool("searchBookPassages", "{\"query\":\"river\",\"userId\":\"victim\"}"),
            tool("searchBookPassages", "{\"query\":\"river\"}"),
            answer());
    var run = service.create(user, "repair-test-key", input());
    assertEquals("draft", run.get("state"));
    verify(retrieval, times(1)).retrieve(user, book, "river", null, false);
    assertTrue(run.get("events").toString().contains("rejected"));
    assertTrue(run.get("events").toString().contains("completed"));
    verify(model, times(3)).turn(anyList(), anyList());
  }

  @Test
  void loopingModelIsStoppedAndProviderFailureDoesNotCreateReviewSession() {
    when(model.turn(anyList(), anyList())).thenAnswer(call -> tool("getReadingProgress", "{}"));
    assertThrows(RuntimeException.class, () -> service.create(user, "loop-limit-key", input()));
    verify(model, times(6)).turn(anyList(), anyList());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
  }

  @Test
  void concurrentConfirmationCreatesOneSession() throws Exception {
    db.update(
        "INSERT INTO review_cards(id,user_id,book_id,source_sentence_key,kind,original_sentence,target_json,fsrs_json,due_at,created_at,updated_at) VALUES(?,?,?,'fixture','wordMeaning','An example sentence.','{\"text\":\"example\"}','{}',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        UUID.randomUUID().toString(),
        user,
        book);
    when(model.turn(anyList(), anyList())).thenReturn(answer());
    String id = (String) service.create(user, "parallel-confirm", input()).get("id");
    try (var pool = Executors.newFixedThreadPool(6)) {
      var tasks = new ArrayList<Callable<Map<String, Object>>>();
      for (int i = 0; i < 12; i++) tasks.add(() -> service.confirm(user, id));
      for (var result : pool.invokeAll(tasks)) assertEquals("confirmed", result.get().get("state"));
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
  }

  @Test
  void invalidInputAndNoMembershipCannotCallModel() throws Exception {
    mvc.perform(
            post("/api/agent-runs")
                .header("Authorization", token)
                .header("Idempotency-Key", "validation-key")
                .contentType("application/json")
                .content("{\"bookId\":\"x\",\"goal\":\"\",\"groupSize\":5}"))
        .andExpect(status().isUnprocessableEntity());
    db.update("DELETE FROM user_entitlements WHERE user_id=?", user);
    assertThrows(RuntimeException.class, () -> service.create(user, "no-membership-key", input()));
    verifyNoInteractions(model);
  }

  @Test
  void providerErrorIsPersistedWithoutConsumingAiQuota() {
    when(model.turn(anyList(), anyList()))
        .thenThrow(
            new com.readquest.exception.ApiException(
                502, "AI_PROVIDER_HTTP_402", "Provider balance unavailable"));
    assertThrows(
        RuntimeException.class, () -> service.create(user, "provider-failure-key", input()));
    var previous = service.list(user).getFirst();
    assertEquals("failed", previous.get("state"));
    assertEquals("AI_PROVIDER_HTTP_402", previous.get("errorCode"));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM feature_usage WHERE user_id=? AND result='allowed'",
            Integer.class,
            user));
  }
}
