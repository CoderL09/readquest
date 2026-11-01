package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.IngestionService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LookupIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired IngestionService imports;
  @Autowired JdbcTemplate db;
  String user, token, book, chapter;

  @BeforeEach
  void setup() throws Exception {
    var r =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.com",
                                "displayName",
                                "Lookup Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(r.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = auth.path("accessToken").asText();
    var admission =
        imports.upload(
            user,
            new MockMultipartFile(
                "file",
                "lookup-test.txt",
                "text/plain",
                "We sat by the bank.".getBytes(StandardCharsets.UTF_8)),
            true);
    book = admission.book().getId();
    imports.processPending();
    chapter = db.queryForObject("SELECT id FROM chapters WHERE book_id=?", String.class, book);
  }

  String auth() {
    return "Bearer " + token;
  }

  Map<String, Object> input() {
    return new HashMap<>(
        Map.of(
            "bookId",
            book,
            "chapterId",
            chapter,
            "paragraphIndex",
            0,
            "type",
            "word",
            "text",
            "bank",
            "sourceSentenceKey",
            "sentence-one",
            "originalSentence",
            "We sat by the bank.",
            "target",
            Map.of("text", "bank", "normalized", "bank", "start", 14, "end", 18)));
  }

  String save(Map<String, Object> in) throws Exception {
    var result =
        mvc.perform(
                post("/api/lookup-items")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content(json.writeValueAsString(in)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString())
        .path("item")
        .path("id")
        .asText();
  }

  @Test
  void repeatedWordOccurrenceIsIdempotent() throws Exception {
    String first = save(input()), second = save(input());
    assertEquals(first, second);
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM lookup_items WHERE user_id=?", Integer.class, user));
  }

  @Test
  void distinctOccurrencesOfSameWordRemainSeparate() throws Exception {
    var first = input();
    first.put("originalSentence", "bank bank");
    first.put("target", Map.of("text", "bank", "start", 0, "end", 4));
    String id = save(first);
    first.put("target", Map.of("text", "bank", "start", 5, "end", 9));
    assertNotEquals(id, save(first));
  }

  @Test
  void enrichAndVocabularyUseStoredData() throws Exception {
    String id = save(input());
    mvc.perform(
            patch("/api/lookup-items/" + id)
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"explanation\":\"河岸\",\"readingStrategy\":\"Observe the context.\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/vocabulary").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].explanation").value("河岸"))
        .andExpect(jsonPath("$.items[0].book.id").value(book))
        .andExpect(jsonPath("$.items[0].mastery.source").value("unobserved"));
  }

  @Test
  void cannotReadOrMutateAnotherUsersLookup() throws Exception {
    String id = save(input()), other = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at)"
            + " VALUES(?,?,'Other','unused',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        other,
        other + "@example.com");
    db.update("UPDATE lookup_items SET user_id=? WHERE id=?", other, id);
    mvc.perform(get("/api/lookup-items/" + id).header("Authorization", auth()))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/lookup-items/" + id).header("Authorization", auth()))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/lookup-items/" + id)
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"explanation\":\"changed\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void targetMustMatchExactSourceSpan() throws Exception {
    var in = input();
    in.put("target", Map.of("text", "other", "start", 14, "end", 18));
    mvc.perform(
            post("/api/lookup-items")
                .header("Authorization", auth())
                .contentType("application/json")
                .content(json.writeValueAsString(in)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void paragraphSummaryKeepsCamelCaseContract() throws Exception {
    save(input());
    mvc.perform(
            get("/api/books/" + book + "/chapters/" + chapter + "/lookup-summary")
                .header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paragraphs[0].paragraphIndex").value(0))
        .andExpect(jsonPath("$.paragraphs[0].total").value(1));
  }
}
