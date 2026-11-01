package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.AiProvider;
import java.util.*;
import java.util.function.Consumer;
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
class ConversationIntegrationTest {
  @org.springframework.test.context.DynamicPropertySource
  static void database(org.springframework.test.context.DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:conversation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean AiProvider provider;
  String user, token;

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
                                "Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,asset_scope,created_at,updated_at)"
            + " VALUES(?,?,'Conversation','completed','private',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user);
    when(provider.configured()).thenReturn(true);
    doAnswer(
        call -> {
          Consumer<String> sink = call.getArgument(2);
          sink.accept("这里的 ");
          sink.accept("sat 表示坐着。");
          return null;
        })
        .when(provider)
        .stream(anyString(), anyString(), any());
  }

  void pro() {
    db.update(
        "INSERT INTO user_entitlements(id,source_order_no,user_id,code,label,starts_at)"
            + " VALUES(?,?,?,'pro','Test',CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        user);
  }

  String input() throws Exception {
    return json.writeValueAsString(
        Map.of(
            "sentence",
            "We sat by the river.",
            "question",
            "sat 是什么意思？",
            "sourceSentenceKey",
            "sentence-one",
            "history",
            List.of()));
  }

  String stream() throws Exception {
    var result =
        mvc.perform(
                post("/api/books/" + user + "/reading-conversations")
                    .header("Authorization", token)
                    .contentType("application/json")
                    .content(input()))
            .andExpect(request().asyncStarted())
            .andReturn();
    result.getAsyncResult(10000);
    return mvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void requiresEntitlementBeforeStartingProvider() throws Exception {
    mvc.perform(
            post("/api/books/" + user + "/reading-conversations")
                .header("Authorization", token)
                .contentType("application/json")
                .content(input()))
        .andExpect(status().isPaymentRequired());
    verify(provider, never()).stream(anyString(), anyString(), any());
  }

  @Test
  void streamsSuggestionsAndCreatesCardsOnlyAfterExplicitConfirmation() throws Exception {
    pro();
    when(provider.json(anyString(), anyString()))
        .thenReturn(
            json.readTree(
                "{\"concepts\":[{\"category\":\"past-tense\",\"reverseQuestionZh\":\"sat"
                    + " 表示什么？\",\"referenceExplanationZh\":\"这里表示坐着。\",\"requiredPointsZh\":[\"sit"
                    + " 的过去式\"],\"targetFragments\":[{\"text\":\"sat\",\"start\":3,\"end\":6}]}]}"));
    String response = stream();
    assertTrue(response.contains("\"type\":\"token\""));
    assertTrue(response.contains("\"type\":\"done\""));
    assertTrue(response.contains("\"status\":\"suggested\""));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_cards WHERE user_id=? AND kind='sentenceConcept'",
            Integer.class,
            user));
    JsonNode suggestion = null;
    for (String line : response.split("\n")) {
      if (!line.startsWith("data:")) continue;
      var event = json.readTree(line.substring(5).strip());
      if (event.path("type").asText().equals("review_concepts"))
        suggestion = event.path("result").path("concepts").get(0);
    }
    assertNotNull(suggestion);
    String input = json.writeValueAsString(suggestion);
    var confirmed =
        mvc.perform(
                post("/api/review-cards")
                    .header("Authorization", token)
                    .contentType("application/json")
                    .content(input))
            .andExpect(status().isCreated())
            .andReturn();
    String card = json.readTree(confirmed.getResponse().getContentAsString()).path("id").asText();
    db.update(
        "UPDATE review_cards SET due_at=TIMESTAMPADD(DAY,1,CURRENT_TIMESTAMP) WHERE id=?", card);
    var due =
        db.queryForObject(
            "SELECT due_at FROM review_cards WHERE id=?", java.sql.Timestamp.class, card);
    mvc.perform(
            post("/api/review-cards")
                .header("Authorization", token)
                .contentType("application/json")
                .content(input))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(false))
        .andExpect(jsonPath("$.id").value(card));
    assertEquals(
        due,
        db.queryForObject(
            "SELECT due_at FROM review_cards WHERE id=?", java.sql.Timestamp.class, card));
    stream();
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_cards WHERE user_id=? AND kind='sentenceConcept'",
            Integer.class,
            user));
  }

  @Test
  void invalidExtractionPreservesAnswerAndCreatesNoCards() throws Exception {
    pro();
    when(provider.json(anyString(), anyString()))
        .thenReturn(json.readTree("{\"concepts\":[{\"category\":\"bad\"}]}"));
    String response = stream();
    assertTrue(response.contains("\"status\":\"failed\""));
    assertTrue(response.contains("\"type\":\"done\""));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM review_cards WHERE user_id=?", Integer.class, user));
  }
}
