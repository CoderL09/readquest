package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.dto.AgentRequest;
import com.readquest.service.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

/** Explicit external integration, isolated from every existing account and mail outbox. */
@SpringBootTest(
    properties = {
      "readquest.local-development=true",
      "readquest.simulated-purchases.enabled=true",
      "readquest.mail.mode=smtp"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(LiveApplicationCheckTest.MailConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LIVE_APP_CHECK_ENABLED", matches = "true")
class LiveApplicationCheckTest {
  private static final String RECIPIENT = System.getenv("LIVE_SMTP_RECIPIENT");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url",
        () ->
            "jdbc:h2:mem:live-check-"
                + LiveApplicationCheckTest.class.getSimpleName()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    properties.add("readquest.storage-root", () -> "./target/live-check/books");
    properties.add("readquest.scene-root", () -> "./target/live-check/scenes");
  }

  @TestConfiguration
  static class MailConfiguration {
    @Bean
    @Primary
    SceneImageProvider replayableImageProvider(
        Environment environment,
        @Qualifier("httpSceneImageProvider") SceneImageProvider delegate,
        CapturingFetcher fetcher) {
      return new SceneImageProvider() {
        public boolean configured() {
          return delegate.configured();
        }

        public byte[] generate(String prompt) {
          String saved = environment.getProperty("LIVE_IMAGE_SOURCE_FILE");
          if (saved == null) return delegate.generate(prompt);
          try {
            return fetcher
                .image(Files.readString(Path.of(saved)), Set.of("s3.siliconflow.cn"))
                .bytes();
          } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
          }
        }
      };
    }

    @Bean
    @Primary
    CapturingFetcher capturingFetcher(@Qualifier("safeRemoteFetcher") RemoteFetcher delegate) {
      return new CapturingFetcher(delegate);
    }

    @Bean
    @Primary
    RecordingMail recordingMail(@Qualifier("activationMailImpl") ActivationMail delegate) {
      return new RecordingMail(delegate);
    }
  }

  static class CapturingFetcher implements RemoteFetcher {
    final RemoteFetcher delegate;
    String imageUrl;

    CapturingFetcher(RemoteFetcher delegate) {
      this.delegate = delegate;
    }

    public Resource fetch(String url) {
      return delegate.fetch(url);
    }

    public Resource fetch(String url, Map<String, String> headers) {
      return delegate.fetch(url, headers);
    }

    public Resource image(String url, Set<String> hosts) {
      imageUrl = url;
      try {
        Files.createDirectories(Path.of("target/live-check"));
        Files.writeString(Path.of("target/live-check/provider-image-url.local.txt"), url);
      } catch (java.io.IOException error) {
        throw new java.io.UncheckedIOException(error);
      }
      return delegate.image(url, hosts);
    }
  }

  static class RecordingMail implements ActivationMail {
    final ActivationMail delegate;
    String code;
    int sent;

    RecordingMail(ActivationMail delegate) {
      this.delegate = delegate;
    }

    public String mode() {
      return delegate.mode();
    }

    public boolean configured() {
      return delegate.configured();
    }

    public void send(String id, String email, String code) {
      if (!RECIPIENT.equals(email))
        throw new IllegalStateException("Only the authorized recipient is permitted");
      delegate.send(id, email, code);
      this.code = code;
      sent++;
    }

    public void removePreview(String id) {
      delegate.removePreview(id);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired DocumentParser parser;
  @Autowired ReadingAiService reading;
  @Autowired AgentService agents;
  @Autowired SceneImageService images;
  @Autowired ActivationService activations;
  @Autowired MembershipService memberships;
  @Autowired RecordingMail mail;
  @Autowired CapturingFetcher fetcher;
  String user, book, chapter, token;
  final Map<String, Object> results = new LinkedHashMap<>();
  final Path output = Path.of("target/live-check");

  @BeforeAll
  void setup() throws Exception {
    Files.createDirectories(output);
    var registration =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                RECIPIENT,
                                "displayName",
                                "Isolated live check",
                                "password",
                                "LiveCheck-Only-Password123!"))))
            .andReturn()
            .getResponse();
    assertEquals(201, registration.getStatus());
    var auth = json.readTree(registration.getContentAsString());
    user = auth.path("user").path("id").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    book = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,visibility,asset_scope,created_at,updated_at) VALUES(?,?,'Local EPUB live check','completed','public','private',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        book,
        user);
    var sections = parser.parse(Path.of(System.getenv("RAG_EVAL_EPUB")));
    for (int i = 0; i < sections.size(); i++) {
      String id = UUID.randomUUID().toString();
      var section = sections.get(i);
      db.update(
          "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content,content_text) VALUES(?,?,?,?,?,?)",
          id,
          book,
          i,
          section.title(),
          section.html(),
          section.text());
      if (i == 8) chapter = id;
    }
    assertNotNull(chapter);
    db.update("UPDATE books SET total_chapters=? WHERE id=?", sections.size(), book);
    db.update(
        "INSERT INTO user_entitlements(id,user_id,code,label,source_order_no,starts_at,ends_at) VALUES(?,?,'pro','Isolated live check',?,CURRENT_TIMESTAMP(3),?)",
        UUID.randomUUID().toString(),
        user,
        UUID.randomUUID().toString(),
        java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
    db.update(
        "INSERT INTO reading_progress(user_id,book_id,chapter_id,total_paragraphs,completed_paragraphs,updated_at) VALUES(?,?,?,20,3,CURRENT_TIMESTAMP(3))",
        user,
        book,
        chapter);
    db.update(
        "INSERT INTO review_cards(id,user_id,book_id,chapter_id,source_sentence_key,kind,original_sentence,target_json,fsrs_json,due_at,created_at,updated_at) VALUES(?,?,?,?,'live-fixture','wordMeaning','The bees gather around the flowers.','{\"text\":\"gather\"}','{}',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
        UUID.randomUUID().toString(),
        user,
        book,
        chapter);
    results.put(
        "isolation",
        "fresh H2 state; production service implementations; no existing outbox scanned");
  }

  @AfterEach
  void save() throws Exception {
    json.writerWithDefaultPrettyPrinter()
        .writeValue(output.resolve("summary.json").toFile(), results);
  }

  @Test
  @Order(1)
  void realBookRagAnswer() throws Exception {
    long start = System.nanoTime();
    try {
      var answer =
          reading.chat(
              user,
              book,
              "What effect does the odor at a bee sting site have on other bees?",
              chapter);
      assertFalse(answer.get("answer").toString().isBlank());
      assertFalse(((List<?>) answer.get("sources")).isEmpty());
      json.writerWithDefaultPrettyPrinter()
          .writeValue(output.resolve("rag-answer.local.json").toFile(), answer);
      results.put(
          "bookRag",
          Map.of(
              "status",
              "passed",
              "durationMs",
              (System.nanoTime() - start) / 1000000,
              "sources",
              ((List<?>) answer.get("sources")).size()));
    } catch (RuntimeException error) {
      results.put("bookRag", Map.of("status", "failed", "reason", error.getMessage()));
      throw error;
    }
  }

  @Test
  @Order(2)
  void realAgentCreatesDraftAndOnlyConfirmsAfterUserAction() throws Exception {
    try {
      var run =
          agents.create(
              user,
              "live-agent-check",
              new AgentRequest(
                  book, chapter, "请先查询我的阅读进度和复习词汇，再检索当前书籍中与 bees 相关的原文，给出简短的三步复习建议。", 5));
      assertEquals("draft", run.get("state"));
      var events = json.valueToTree(run.get("events"));
      assertFalse(events.isEmpty());
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
      String id = (String) run.get("id");
      assertEquals("confirmed", agents.confirm(user, id).get("state"));
      agents.confirm(user, id);
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM review_sessions WHERE user_id=?", Integer.class, user));
      json.writerWithDefaultPrettyPrinter()
          .writeValue(output.resolve("agent-result.local.json").toFile(), run);
      results.put(
          "agent", Map.of("status", "passed", "tools", events, "confirmationIdempotent", true));
    } catch (RuntimeException error) {
      results.put("agent", Map.of("status", "failed", "reason", error.getMessage()));
      throw error;
    }
  }

  @Test
  @Order(3)
  void realImageGenerationAndSignedRetrieval() throws Exception {
    try {
      var generated =
          mvc.perform(
                  post("/api/books/" + book + "/scene-images")
                      .header("Authorization", token)
                      .contentType("application/json")
                      .content(
                          json.writeValueAsString(
                              Map.of(
                                  "prompt",
                                  "A peaceful reading room with a wooden desk and warm daylight, watercolor illustration, no text."))))
              .andReturn()
              .getResponse();
      assertEquals(201, generated.getStatus());
      String url = json.readTree(generated.getContentAsString()).path("imageUrl").asText();
      var response = mvc.perform(get(url)).andReturn().getResponse();
      assertEquals(200, response.getStatus());
      assertTrue(response.getContentAsByteArray().length > 1000);
      results.put(
          "image",
          Map.of(
              "status",
              "passed",
              "bytes",
              response.getContentAsByteArray().length,
              "signedRetrieval",
              true,
              "generationSource",
              System.getenv("LIVE_IMAGE_SOURCE_FILE") == null
                  ? "live_provider"
                  : "reused_previous_live_generation"));
    } catch (RuntimeException error) {
      results.put("image", Map.of("status", "failed", "reason", error.getMessage()));
      throw error;
    }
  }

  @Test
  @Order(4)
  void realSmtpOutboxAndSingleUseRedemption() {
    try {
      assertTrue(mail.configured());
      var purchase = activations.create(user, RECIPIENT, "live-smtp-purchase");
      String id = (String) purchase.get("id");
      activations.confirm(user, id);
      activations.deliver();
      assertEquals("sent", activations.get(user, id).get("deliveryStatus"));
      assertEquals(1, mail.sent);
      assertNotNull(mail.code);
      assertNotNull(memberships.redeem(user, mail.code));
      assertThrows(RuntimeException.class, () -> memberships.redeem(user, mail.code));
      results.put(
          "smtp",
          Map.of(
              "status",
              "accepted_by_smtp",
              "recipient",
              RECIPIENT,
              "sent",
              mail.sent,
              "singleUseRedemption",
              true,
              "inboxDelivery",
              "requires_recipient_confirmation"));
    } catch (RuntimeException error) {
      results.put("smtp", Map.of("status", "failed", "reason", error.getMessage()));
      throw error;
    }
  }
}
