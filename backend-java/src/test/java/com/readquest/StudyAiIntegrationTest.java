package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.AiProvider;
import com.readquest.service.EmbeddingProvider;
import java.util.*;
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
class StudyAiIntegrationTest {
  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:studyai;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired com.readquest.service.ReadingService reading;
  @MockitoBean AiProvider provider;
  @MockitoBean EmbeddingProvider embeddings;
  String token, user, chapter;
  final String passage =
      "The river crossed the valley. The train carried many people. The woman folded a newspaper."
          + " The children watched the fields. The station appeared in the distance.";

  @BeforeEach
  void setup() throws Exception {
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
                                "Study Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    chapter = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,asset_scope,created_at,updated_at)"
            + " VALUES(?,?,'Study','completed','private',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user);
    db.update(
        "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content) VALUES(?,?,0,'One',?)",
        chapter,
        user,
        passage);
    when(provider.configured()).thenReturn(true);
  }

  void pro() {
    db.update(
        "INSERT INTO user_entitlements(id,source_order_no,user_id,code,label,starts_at)"
            + " VALUES(?,?,?,'pro','Test',CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        user);
  }

  JsonNode quiz() {
    var result = json.createObjectNode();
    var questions = result.putArray("questions");
    String[] targets = {"river", "train", "woman", "children", "station"};
    String[] sentences = passage.split("(?<=\\.) ");
    for (int i = 0; i < 5; i++) {
      var q =
          questions
              .addObject()
              .put("word", targets[i])
              .put("correctAnswer", targets[i])
              .put("sentence", sentences[i].replace(targets[i], "____"));
      q.putArray("options").add(targets[i]).add("door").add("lamp").add("window");
    }
    return result;
  }

  @Test
  void contextAnalysisRequiresProUsesOnlyPastTextAndValidatesOutput() throws Exception {
    String endpoint = "/api/books/" + user + "/context-analyses";
    String input =
        json.writeValueAsString(Map.of("currentSentence", "The woman folded a newspaper."));
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isPaymentRequired());
    pro();
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isServiceUnavailable());
    when(embeddings.configured()).thenReturn(true);
    when(embeddings.modelKey()).thenReturn("test-model");
    when(embeddings.embed(anyList()))
        .thenAnswer(call ->
            ((List<String>) call.getArgument(0)).stream().map(t -> new double[] {1, 0}).toList());
    var result = json.createObjectNode();
    for (String field :
        List.of(
            "emotion_or_vibe",
            "cinematic_visual",
            "contextual_hint",
            "reading_expectation",
            "why_it_feels_confusing",
            "simple_english_rephrase")) result.put(field, "A quiet journey.");
    when(provider.json(anyString(), anyString())).thenReturn(result);
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk())
        .andExpect(jsonPath("emotion_or_vibe").value("A quiet journey."));
    verify(provider)
        .json(
            anyString(),
            argThat(content ->
                content.contains("The river crossed the valley.")
                    && !content.contains("The station appeared")));
    assertTrue(
        db.queryForObject(
                "SELECT COUNT(*) FROM book_embeddings WHERE book_id=?", Integer.class, user)
            > 0);
    when(provider.json(anyString(), anyString()))
        .thenReturn(json.createObjectNode().put("wrong", "value"));
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isBadGateway());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM feature_usage WHERE user_id=? AND feature='module1_analysis' AND"
                + " result='provider_failed'",
            Integer.class,
            user));
  }

  @Test
  void quizzesRequireEntitlementThenCacheVerifiedQuestions() throws Exception {
    mvc.perform(post("/api/chapters/" + chapter + "/quizzes").header("Authorization", token))
        .andExpect(status().isPaymentRequired());
    verify(provider, never()).json(anyString(), anyString());
    pro();
    when(provider.json(anyString(), anyString())).thenReturn(quiz());
    for (int i = 0; i < 2; i++)
      mvc.perform(post("/api/chapters/" + chapter + "/quizzes").header("Authorization", token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.questions.length()").value(5));
    mvc.perform(get("/api/chapters/" + chapter + "/quiz").header("Authorization", token))
        .andExpect(status().isOk());
    verify(provider, times(1)).json(anyString(), anyString());
  }

  @Test
  void rejectsInventedQuizSentencesAndRefundsUsage() throws Exception {
    pro();
    var result = quiz();
    ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("questions").get(0))
        .put("sentence", "This ____ was never in the passage.");
    when(provider.json(anyString(), anyString())).thenReturn(result);
    mvc.perform(post("/api/chapters/" + chapter + "/quizzes").header("Authorization", token))
        .andExpect(status().isBadGateway());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM feature_usage WHERE user_id=? AND result='allowed'",
            Integer.class,
            user));
    assertEquals(
        "failed",
        db.queryForObject(
            "SELECT status FROM chapter_quizzes WHERE chapter_id=?", String.class, chapter));
  }

  @Test
  void cannotPublishQuizAfterSourceBecomesUnreadable() throws Exception {
    pro();
    when(provider.json(anyString(), anyString()))
        .thenAnswer(call -> {
          db.update("UPDATE books SET status='deleted' WHERE id=?", user);
          return quiz();
        });
    mvc.perform(post("/api/chapters/" + chapter + "/quizzes").header("Authorization", token))
        .andExpect(status().isNotFound());
    assertEquals(
        "failed",
        db.queryForObject(
            "SELECT status FROM chapter_quizzes WHERE chapter_id=?", String.class, chapter));
  }

  @Test
  void interventionValidatesStructuredAnswer() throws Exception {
    pro();
    when(provider.json(anyString(), anyString()))
        .thenReturn(
            json.readTree(
                "{\"prediction_question\":\"What happens"
                    + " next?\",\"options\":[\"Walk\",\"Wait\",\"Read\"],\"correct_answer_index\":1,\"logic_feedback\":\"The"
                    + " train is late.\"}"));
    mvc.perform(
            post("/api/reading-interventions")
                .header("Authorization", token)
                .contentType("application/json")
                .content(
                    "{\"surroundingText\":\"The train was late.\",\"stuckChunk\":\"She waited.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.intervention_result.correct_answer_index").value(1));
    when(provider.json(anyString(), anyString())).thenReturn(json.createObjectNode());
    mvc.perform(
            post("/api/reading-interventions")
                .header("Authorization", token)
                .contentType("application/json")
                .content(
                    "{\"surroundingText\":\"The train was late.\",\"stuckChunk\":\"She waited.\"}"))
        .andExpect(status().isBadGateway());
  }

  @Test
  void bookChatReturnsServerBoundLocationsAndChecksAccess() throws Exception {
    pro();
    when(provider.text(anyString(), anyString())).thenReturn("The river crossed the valley. [1]");
    String endpoint = "/api/books/" + user + "/chat-messages";
    var response =
        mvc.perform(
                post(endpoint)
                    .header("Authorization", token)
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("question", "Where was the river?", "chapterId", chapter))))
            .andExpect(status().isOk())
            .andReturn();
    var source = json.readTree(response.getResponse().getContentAsString()).path("sources").get(0);
    assertEquals(user, source.path("bookId").asText());
    assertEquals(chapter, source.path("stageId").asText());
    assertEquals(1, source.path("citation").asInt());
    assertEquals(
        passage,
        passage.substring(source.path("startOffset").asInt(), source.path("endOffset").asInt()));
    assertEquals(
        com.readquest.service.impl.BookRetrievalServiceImpl.sourceHash(passage),
        source.path("chapterHash").asText());
    verify(provider).text(contains("[1]"), contains("[1] Chapter: One"));
    clearInvocations(provider);
    mvc.perform(
            post(endpoint.replace(user, UUID.randomUUID().toString()))
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"question\":\"Where was the river?\"}"))
        .andExpect(status().isNotFound());
    verify(provider, never()).text(anyString(), anyString());
  }

  @Test
  void sentenceGroupsPreserveSlashesAndCacheStructuredGroups() throws Exception {
    pro();
    String sentence = "We can read and/or listen.";
    db.update("UPDATE chapters SET content=? WHERE id=?", sentence, chapter);
    when(provider.json(anyString(), anyString()))
        .thenReturn(json.readTree("{\"sense_groups\":[\"We can read\",\"and/or listen.\"]}"));
    String endpoint = "/api/books/" + user + "/chapters/" + chapter + "/sentence-chunks";
    String input = json.writeValueAsString(Map.of("sentenceId", chapter + ":p0:s0"));
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"))
        .andExpect(jsonPath("$.source").value("model"))
        .andExpect(jsonPath("$.sentences[0].chunks[1].text").value("and/or listen."))
        .andExpect(jsonPath("$.sentences[0].chunks[1].start").value(12));
    when(provider.configured()).thenReturn(false);
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("cache"))
        .andExpect(jsonPath("$.sentences[0].chunks.length()").value(2));
    verify(provider, times(1)).json(anyString(), anyString());
  }

  @Test
  void malformedGroupsNeverReplaceSourceTextOrEnterCache() throws Exception {
    pro();
    String original = "The river crossed the valley.";
    String endpoint = "/api/books/" + user + "/chapters/" + chapter + "/sentence-chunks";
    String input = json.writeValueAsString(Map.of("sentenceId", chapter + ":p0:s0"));
    for (var groups :
        List.of(
            List.of("The river", "the valley."),
            List.of("The river", "crossed the sea."),
            List.of("crossed the valley.", "The river"),
            List.of("The river", "The river", "crossed the valley."),
            List.of("The river", "crossed"),
            List.of("The river", "", "crossed the valley."))) {
      when(provider.json(anyString(), anyString()))
          .thenReturn(json.valueToTree(Map.of("sense_groups", groups)));
      mvc.perform(
              post(endpoint)
                  .header("Authorization", token)
                  .contentType("application/json")
                  .content(input))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("unavailable"))
          .andExpect(jsonPath("$.source").value("fallback"))
          .andExpect(jsonPath("$.sentences[0].chunks[0].text").value(original));
    }
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM ai_analysis_cache WHERE user_id=?", Integer.class, user));
  }

  @Test
  void sentenceAnalysisIsBoundToBookChapterAndServerText() throws Exception {
    pro();
    String endpoint = "/api/books/" + user + "/chapters/" + chapter + "/sentence-chunks";
    when(provider.json(anyString(), anyString()))
        .thenReturn(json.readTree("{\"sense_groups\":[\"The river\",\"crossed the valley.\"]}"));
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("sentenceId", chapter + ":p0:s0", "text", "Client replacement"))))
        .andExpect(status().isUnprocessableEntity());
    verify(provider, never()).json(anyString(), anyString());
    mvc.perform(
            post(endpoint)
                .header("Authorization", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("sentenceId", chapter + ":p0:s0"))))
        .andExpect(status().isOk());
    verify(provider)
        .json(
            anyString(),
            argThat(value ->
                value.contains("The river crossed the valley.")
                    && !value.contains("Client replacement")));
    clearInvocations(provider);
    for (String path : List.of(endpoint.replace(user, UUID.randomUUID().toString()), endpoint)) {
      mvc.perform(
              post(path)
                  .header("Authorization", token)
                  .contentType("application/json")
                  .content("{\"sentenceId\":\"another-chapter:p0:s0\"}"))
          .andExpect(status().isNotFound());
    }
    verify(provider, never()).json(anyString(), anyString());
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
      named = "RAG_EVAL_EPUB",
      matches = ".+")
  void realEpubSentencesRoundTripThroughScopedEndpointWithoutExternalCalls() throws Exception {
    pro();
    var parsed =
        new com.readquest.service.impl.DocumentParserImpl()
            .parseBook(java.nio.file.Path.of(System.getenv("RAG_EVAL_EPUB")));
    String content =
        parsed.sections().stream()
            .filter(s -> s.text().length() > 1800)
            .findFirst()
            .orElseThrow()
            .text();
    db.update("UPDATE chapters SET content=? WHERE id=?", content, chapter);
    when(provider.json(anyString(), anyString()))
        .thenAnswer(call -> {
          String input = call.getArgument(1);
          String sentence =
              input.substring(
                  input.indexOf("<paragraph>\n") + 12, input.lastIndexOf("\n</paragraph>"));
          int boundary = sentence.lastIndexOf(' ', sentence.length() / 2);
          var groups =
              boundary > 0
                  ? List.of(
                      sentence.substring(0, boundary).strip(), sentence.substring(boundary).strip())
                  : List.of(sentence);
          return json.valueToTree(Map.of("sense_groups", groups));
        });
    JsonNode structure = json.valueToTree(reading.structure(user, chapter));
    int checked = 0;
    for (var paragraph : structure.path("paragraphs")) {
      for (var sentence : paragraph.path("sentences")) {
        if (sentence.path("text").asText().length() < 30
            || sentence.path("text").asText().length() > 2000) continue;
        var response =
            mvc.perform(
                    post("/api/books/" + user + "/chapters/" + chapter + "/sentence-chunks")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of("sentenceId", sentence.path("id").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andReturn();
        var result =
            json.readTree(response.getResponse().getContentAsString()).path("sentences").get(0);
        assertEquals(sentence.path("text").asText(), result.path("text").asText());
        for (var group : result.path("chunks")) {
          assertEquals(
              group.path("text").asText(),
              result
                  .path("text")
                  .asText()
                  .substring(group.path("start").asInt(), group.path("end").asInt()));
        }
        var cardInput =
            Map.of(
                "bookId",
                user,
                "chapterId",
                chapter,
                "sourceSentenceKey",
                sentence.path("id").asText(),
                "originalSentence",
                sentence.path("text").asText(),
                "category",
                "reading-comprehension",
                "reverseQuestionZh",
                "这句话应如何理解？",
                "referenceExplanationZh",
                "本地测试参考理解",
                "requiredPointsZh",
                List.of("结合语境"));
        var confirmed =
            mvc.perform(
                    post("/api/review-cards")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(json.writeValueAsString(cardInput)))
                .andExpect(status().isCreated())
                .andReturn();
        String cardId =
            json.readTree(confirmed.getResponse().getContentAsString()).path("id").asText();
        var sourceResponse =
            mvc.perform(
                    get("/api/review-cards/" + cardId + "/source").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        var source = json.readTree(sourceResponse.getResponse().getContentAsString());
        assertEquals(
            sentence.path("text").asText(),
            content.substring(
                source.path("startOffset").asInt(), source.path("endOffset").asInt()));
        checked++;
        if (checked == 8) break;
      }
      if (checked == 8) break;
    }
    assertEquals(8, checked);
  }
}
