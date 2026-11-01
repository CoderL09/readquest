package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReviewIntegrationTest.ProviderConfiguration.class)
class ReviewIntegrationTest {
  @TestConfiguration
  static class ProviderConfiguration {
    @Bean
    @Primary
    GradingProvider gradingProvider(ObjectMapper json) {
      return new GradingProvider(json);
    }
  }

  static class GradingProvider implements AiProvider {
    final ObjectMapper json;
    final AtomicInteger calls = new AtomicInteger();
    double score = 0.95;
    String hint = "结合前面的介词和动作来判断。";
    boolean invalid = false;
    Runnable hook = () -> {};

    GradingProvider(ObjectMapper j) {
      json = j;
    }

    public boolean configured() {
      return true;
    }

    public JsonNode json(String system, String input) {
      calls.incrementAndGet();
      hook.run();
      if (invalid) return json.createObjectNode().put("unexpected", true);
      var value =
          json.createObjectNode()
              .put("score", score)
              .put("criticalMisconception", false)
              .put("feedbackZh", "请结合原句检查语义关系。")
              .put("contextHintZh", hint);
      value.putArray("acknowledgedPointsZh").add("注意到了语境");
      value.putArray("missingPointsZh").add("具体含义");
      return value;
    }

    public String text(String system, String input) {
      throw new AssertionError("No external text provider should be called");
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    // A second Spring context needs a separate H2 DB. Real-MySQL runs use their explicit dedicated
    // DB.
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      registry.add(
          "spring.datasource.url",
          () ->
              "jdbc:h2:mem:review-state-machine;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired IngestionService imports;
  @Autowired GradingProvider provider;
  String user, token, book, chapter;

  @BeforeEach
  void setup() throws Exception {
    provider.calls.set(0);
    provider.score = 0.95;
    provider.hint = "结合前面的介词和动作来判断。";
    provider.invalid = false;
    provider.hook = () -> {};
    var result =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.com",
                                "displayName",
                                "Review Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = auth.path("accessToken").asText();
    var admission =
        imports.upload(
            user,
            new MockMultipartFile(
                "file",
                "review.txt",
                "text/plain",
                "We sat by the bank.".getBytes(StandardCharsets.UTF_8)),
            true);
    book = admission.book().getId();
    imports.processPending();
    chapter = db.queryForObject("SELECT id FROM chapters WHERE book_id=?", String.class, book);
    db.update(
        "INSERT INTO user_entitlements(id,source_order_no,user_id,code,label,starts_at,ends_at)"
            + " VALUES(?,?,?,'pro','Test"
            + " Pro',TIMESTAMPADD(DAY,-1,CURRENT_TIMESTAMP),TIMESTAMPADD(DAY,1,CURRENT_TIMESTAMP))",
        UUID.randomUUID().toString(),
        "TEST-" + UUID.randomUUID(),
        user);
  }

  @Test
  void reviewCardsKeepTheirOriginAndSourceLookupChecksOwnership() throws Exception {
    lookup();
    var session = start();
    String cardId = session.path("card").path("id").asText();
    assertEquals(book, session.path("card").path("bookId").asText());
    assertEquals(chapter, session.path("card").path("chapterId").asText());
    mvc.perform(get("/api/review-cards/" + cardId + "/source").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("We sat by the bank."))
        .andExpect(jsonPath("$.startOffset").value(0));
    mvc.perform(
            get("/api/review-cards/" + UUID.randomUUID() + "/source")
                .header("Authorization", auth()))
        .andExpect(status().isNotFound());
    var other =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "displayName",
                                "Other",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    String otherToken =
        json.readTree(other.getResponse().getContentAsString()).path("accessToken").asText();
    mvc.perform(
            get("/api/review-cards/" + cardId + "/source")
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());
  }

  String auth() {
    return "Bearer " + token;
  }

  String lookup() throws Exception {
    var body =
        Map.of(
            "bookId",
            book,
            "chapterId",
            chapter,
            "sourceSentenceKey",
            "sentence",
            "type",
            "word",
            "text",
            "bank",
            "originalSentence",
            "We sat by the bank.",
            "explanation",
            "河岸",
            "target",
            Map.of("text", "bank", "start", 14, "end", 18));
    var result =
        mvc.perform(
                post("/api/lookup-items")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    String lookup =
        json.readTree(result.getResponse().getContentAsString()).path("item").path("id").asText();
    db.update(
        "UPDATE review_cards SET due_at=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE"
            + " lookup_item_id=?",
        lookup);
    return lookup;
  }

  JsonNode start() throws Exception {
    var result =
        mvc.perform(
                post("/api/review-sessions")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content("{\"groupSize\":5}"))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("session");
  }

  JsonNode answer(JsonNode session, String kind, String stage, String key) throws Exception {
    var result =
        mvc.perform(
                post("/api/review-sessions/"
                        + session.path("id").asText()
                        + "/cards/"
                        + session.path("card").path("id").asText()
                        + "/answers")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "answerKind",
                                kind,
                                "stage",
                                stage,
                                "answer",
                                "我的语境解释",
                                "responseTimeMs",
                                1000,
                                "idempotencyKey",
                                key))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  JsonNode advance(JsonNode session, String event) throws Exception {
    var result =
        mvc.perform(
                post("/api/review-sessions/" + session.path("id").asText() + "/progressions")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("eventId", event))))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("session");
  }

  @Test
  void correctAnswerIsScheduledOnceAndInitialReferenceIsHidden() throws Exception {
    lookup();
    var session = start();
    assertFalse(session.path("card").has("referenceAnswerZh"));
    String key = UUID.randomUUID().toString();
    var result = answer(session, "wordMeaning", "initial", key);
    assertTrue(result.path("ratingApplied").asBoolean());
    assertEquals("Good", result.path("derivedRating").asText());
    assertEquals("河岸", result.path("contextMeaningZh").asText());
    answer(session, "wordMeaning", "initial", key);
    assertEquals(1, provider.calls.get());
    String stored =
        db.queryForObject("SELECT fsrs_json FROM review_cards WHERE user_id=?", String.class, user);
    assertEquals(1, json.readTree(stored).path("reps").asInt());
    mvc.perform(get("/api/vocabulary").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].mastery.source").value("fsrs"))
        .andExpect(jsonPath("$.items[0].mastery.answerQuality").value(95.0));
  }

  @Test
  void guidedRevisionDoesNotOverwriteInitialScheduling() throws Exception {
    lookup();
    var session = start();
    provider.score = 0.1;
    var first = answer(session, "wordMeaning", "initial", UUID.randomUUID().toString());
    assertEquals("hintShown", first.path("currentStage").asText());
    assertEquals("Again", first.path("derivedRating").asText());
    String event = UUID.randomUUID().toString();
    var revision = advance(session, event);
    assertEquals("guidedRevision", revision.path("currentStage").asText());
    assertEquals("guidedRevision", advance(session, event).path("currentStage").asText());
    provider.score = 0.95;
    var result = answer(revision, "wordMeaning", "guidedRevision", UUID.randomUUID().toString());
    assertFalse(result.path("ratingApplied").asBoolean());
    assertEquals("referenceShown", result.path("currentStage").asText());
    assertEquals(
        1,
        json.readTree(
                db.queryForObject(
                    "SELECT fsrs_json FROM review_cards WHERE user_id=?", String.class, user))
            .path("reps")
            .asInt());
  }

  @Test
  void completedCelebrationIsIdempotent() throws Exception {
    lookup();
    var session = start();
    answer(session, "wordMeaning", "initial", UUID.randomUUID().toString());
    var completed = advance(session, UUID.randomUUID().toString());
    assertEquals("celebrating", completed.path("state").asText());
    String endpoint = "/api/review-sessions/" + session.path("id").asText() + "/completions";
    for (int i = 0; i < 2; i++)
      mvc.perform(post(endpoint).header("Authorization", auth()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.nextSession").isEmpty());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
  }

  @Test
  void invalidGradeDoesNotAdvanceFsrsAndSessionCanResume() throws Exception {
    lookup();
    var session = start();
    provider.invalid = true;
    var result = answer(session, "wordMeaning", "initial", UUID.randomUUID().toString());
    assertEquals("failed", result.path("gradingState").asText());
    assertFalse(result.path("ratingApplied").asBoolean());
    assertEquals("initialAnswer", result.path("currentStage").asText());
    assertEquals(
        0,
        json.readTree(
                db.queryForObject(
                    "SELECT fsrs_json FROM review_cards WHERE user_id=?", String.class, user))
            .path("reps")
            .asInt());
    mvc.perform(get("/api/review-sessions/current").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.session.submission.gradingState").value("failed"));
  }

  @Test
  void hintsCannotRevealTheWordAnswer() throws Exception {
    lookup();
    var session = start();
    provider.score = 0.1;
    provider.hint = "这里指河岸。";
    var result = answer(session, "wordMeaning", "initial", UUID.randomUUID().toString());
    assertEquals("HINT_LEAK", result.path("failureCode").asText());
    assertFalse(result.path("ratingApplied").asBoolean());
  }

  @Test
  void deletedLookupIsRemovedFromCurrentSession() throws Exception {
    String lookup = lookup();
    start();
    mvc.perform(delete("/api/lookup-items/" + lookup).header("Authorization", auth()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/review-sessions/current").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.session.state").value("celebrating"))
        .andExpect(jsonPath("$.session.card").isEmpty());
  }

  @Test
  void conceptNeedsTransferBeforeScheduling() throws Exception {
    var input =
        Map.of(
            "bookId",
            book,
            "chapterId",
            chapter,
            "sourceSentenceKey",
            "sentence",
            "originalSentence",
            "We sat by the bank.",
            "category",
            "介词短语",
            "reverseQuestionZh",
            "by 在句中起什么作用？",
            "referenceExplanationZh",
            "引出地点，表示在旁边。",
            "requiredPointsZh",
            List.of("引出地点", "表示在旁边"),
            "targetFragments",
            List.of(Map.of("text", "by the bank", "start", 7, "end", 18)));
    var result =
        mvc.perform(
                post("/api/review-cards")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(json.writeValueAsString(input)))
            .andExpect(status().isCreated())
            .andReturn();
    String card = json.readTree(result.getResponse().getContentAsString()).path("id").asText();
    db.update(
        "UPDATE review_cards SET due_at=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE id=?",
        card);
    var session = start();
    var explanation =
        answer(session, "conceptExplanation", "initial", UUID.randomUUID().toString());
    assertEquals("transferSentence", explanation.path("currentStage").asText());
    assertFalse(explanation.path("ratingApplied").asBoolean());
    provider.score = 0.1;
    var transfer = answer(session, "transferSentence", "initial", UUID.randomUUID().toString());
    assertTrue(transfer.path("ratingApplied").asBoolean());
    assertEquals("Hard", transfer.path("derivedRating").asText());
  }

  @Test
  void sessionMustBelongToCurrentUser() throws Exception {
    lookup();
    var session = start();
    String other = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at)"
            + " VALUES(?,?,'Other','unused',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        other,
        other + "@example.com");
    db.update(
        "UPDATE review_sessions SET user_id=?,active_owner=? WHERE id=?",
        other,
        other,
        session.path("id").asText());
    mvc.perform(
            get("/api/review-sessions/" + session.path("id").asText())
                .header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }
}
