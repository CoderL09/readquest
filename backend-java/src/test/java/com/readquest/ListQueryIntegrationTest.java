package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ListQueryIntegrationTest.CounterConfig.class)
class ListQueryIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:list_queries;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @TestConfiguration
  static class CounterConfig {
    @Bean
    QueryCounter queryCounter() {
      return new QueryCounter();
    }
  }

  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "query",
          args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
  public static class QueryCounter implements Interceptor {
    private final List<String> statements = new ArrayList<>();

    public Object intercept(Invocation invocation) throws Throwable {
      statements.add(((MappedStatement) invocation.getArgs()[0]).getId());
      return invocation.proceed();
    }

    void clear() {
      statements.clear();
    }

    long count(String suffix) {
      return statements.stream().filter(id -> id.endsWith(suffix)).count();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired QueryCounter queries;
  String user, token, otherToken, book;

  JsonNode register() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "password",
                                "ListQuery123!",
                                "displayName",
                                "Synthetic reader"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @BeforeEach
  void setup() throws Exception {
    var account = register();
    user = account.path("user").path("id").asText();
    token = "Bearer " + account.path("accessToken").asText();
    otherToken = "Bearer " + register().path("accessToken").asText();
    book = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO books(id,owner_id,title,author,status,asset_scope,visibility,moderation_status,created_at,updated_at) VALUES(?,?,'Synthetic book','Author','completed','private','public','unreviewed',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        book,
        user);
    db.update(
        "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content) VALUES(?,?,0,'Synthetic chapter','The river was still.')",
        book,
        book);
  }

  @Test
  void noteListReturnsMetadataInTwoBusinessQueriesAndKeepsUserIsolation() throws Exception {
    String id = null;
    for (int i = 0; i < 12; i++)
      id =
          json.readTree(
                  mvc.perform(
                          post("/api/book-notes")
                              .header("Authorization", token)
                              .contentType("application/json")
                              .content(
                                  json.writeValueAsString(
                                      Map.of(
                                          "bookId",
                                          book,
                                          "chapterId",
                                          book,
                                          "quote",
                                          "Synthetic quote " + i,
                                          "note",
                                          "Note"))))
                      .andExpect(status().isCreated())
                      .andExpect(jsonPath("$.book.title").value("Synthetic book"))
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .path("id")
              .asText();
    queries.clear();
    mvc.perform(get("/api/book-notes").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(12))
        .andExpect(jsonPath("$.items[0].book.title").value("Synthetic book"))
        .andExpect(jsonPath("$.items[0].chapter.title").value("Synthetic chapter"));
    assertEquals(1, queries.count("NoteMapper.list"));
    assertEquals(1, queries.count("NoteMapper.count"));
    assertEquals(0, queries.count("BookMapper.findById"));
    assertEquals(0, queries.count("BookMapper.chapter"));
    mvc.perform(get("/api/book-notes/" + id).header("Authorization", otherToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void libraryEmbedsAllBookDetailsWithOneBatchQuery() throws Exception {
    mvc.perform(
            put("/api/users/me/library/books/" + book)
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"shelfState\":\"saved\"}"))
        .andExpect(status().isOk());
    queries.clear();
    mvc.perform(get("/api/users/me/library/books").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].book.title").value("Synthetic book"));
    assertEquals(1, queries.count("LibraryMapper.list"));
    assertEquals(1, queries.count("LibraryMapper.count"));
    assertEquals(1, queries.count("BookMapper.accessibleMany"));
    assertEquals(0, queries.count("BookMapper.findById"));
  }

  @Test
  void uploadViewAndPaginationAreEnforcedOnTheServer() throws Exception {
    mvc.perform(get("/api/books").param("view", "uploads").header("Authorization", otherToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/books").param("view", "uploads").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/api/books")
                .param("page", "102")
                .param("pageSize", "100")
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INVALID_PAGINATION"));
  }
}
