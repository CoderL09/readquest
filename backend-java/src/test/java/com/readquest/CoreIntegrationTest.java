package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired PasswordEncoder passwords;
  private String token, user, refresh;

  @BeforeEach
  void setup() throws Exception {
    String email = UUID.randomUUID() + "@example.com";
    MvcResult r =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                email,
                                "displayName",
                                "Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode n = json.readTree(r.getResponse().getContentAsString());
    token = n.path("accessToken").asText();
    user = n.path("user").path("id").asText();
    refresh = r.getResponse().getCookie("rq_refresh").getValue();
    db.update(
        "INSERT INTO"
            + " books(id,owner_id,title,author,status,asset_scope,visibility,moderation_status,created_at,updated_at)"
            + " VALUES(?,?,'Test"
            + " book','Author','completed','private','public','unreviewed',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user);
    db.update(
        "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content) VALUES(?,?,0,'Chapter"
            + " One','Hello world')",
        user,
        user);
  }

  private String book() {
    return user;
  }

  private String chapter() {
    return user;
  }

  private String auth() {
    return "Bearer " + token;
  }

  private MvcResult note(String chapter) throws Exception {
    return mvc.perform(
            post("/api/book-notes")
                .header("Authorization", auth())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "bookId",
                            book(),
                            "chapterId",
                            chapter,
                            "quote",
                            "Hello",
                            "note",
                            "A note"))))
        .andReturn();
  }

  @Test
  void authenticationAndPasswordHash() throws Exception {
    mvc.perform(get("/api/users/me").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    String hash =
        db.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, user);
    assertTrue(passwords.matches("Password123!", hash));
    String email = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, user);
    mvc.perform(
            post("/api/sessions")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", email, "password", "Password123!"))))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/sessions")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void noAuthAndTamperedTokenRejected() throws Exception {
    mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/books")
                .header(
                    "Authorization",
                    "Bearer " + token.substring(0, token.lastIndexOf('.') + 1) + "invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRotatesWithoutInvalidatingAnotherTabsUnexpiredAccess() throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/sessions/current/tokens")
                    .header("Authorization", "Bearer expired-token")
                    .cookie(new Cookie("rq_refresh", refresh)))
            .andExpect(status().isOk())
            .andReturn();
    String next = json.readTree(r.getResponse().getContentAsString()).path("accessToken").asText();
    assertNotEquals(refresh, r.getResponse().getCookie("rq_refresh").getValue());
    mvc.perform(post("/api/sessions/current/tokens").cookie(new Cookie("rq_refresh", refresh)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users/me").header("Authorization", auth())).andExpect(status().isOk());
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + next))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/sessions/current").cookie(r.getResponse().getCookie("rq_refresh")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/users/me").header("Authorization", auth()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + next))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesAccess() throws Exception {
    mvc.perform(delete("/api/sessions/current").cookie(new Cookie("rq_refresh", refresh)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/users/me").header("Authorization", auth()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void inactiveAccountRejected() throws Exception {
    db.update("UPDATE users SET status='suspended' WHERE id=?", user);
    mvc.perform(get("/api/books").header("Authorization", auth()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void catalogAndChapterRespectVisibility() throws Exception {
    mvc.perform(
            get("/api/books/" + book() + "/chapters/" + chapter()).header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("Hello world"));
    db.update("UPDATE books SET visibility='removed' WHERE id=?", book());
    mvc.perform(get("/api/books/" + book()).header("Authorization", auth()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/books/" + book() + "/chapters").header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void privateForeignBookHidden() throws Exception {
    String other = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at)"
            + " VALUES(?,?,'Other','unused',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        other,
        other + "@example.com");
    db.update("UPDATE books SET owner_id=?,visibility='unlisted' WHERE id=?", other, book());
    mvc.perform(get("/api/books/" + book()).header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void notesPersistAndCanBeUpdatedAndDeleted() throws Exception {
    MvcResult r = note(chapter());
    assertEquals(201, r.getResponse().getStatus());
    String id = json.readTree(r.getResponse().getContentAsString()).path("id").asText();
    mvc.perform(
            patch("/api/book-notes/" + id)
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"note\":\"Updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note").value("Updated"));
    assertEquals(
        "Updated", db.queryForObject("SELECT note FROM book_notes WHERE id=?", String.class, id));
    mvc.perform(get("/api/book-notes/" + id).header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note").value("Updated"));
    mvc.perform(delete("/api/book-notes/" + id).header("Authorization", auth()))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/book-notes/" + id).header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void notesAreIsolatedByUser() throws Exception {
    MvcResult r = note(chapter());
    String id = json.readTree(r.getResponse().getContentAsString()).path("id").asText();
    String other = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at)"
            + " VALUES(?,?,'Other','unused',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        other,
        other + "@example.com");
    db.update("UPDATE book_notes SET user_id=? WHERE id=?", other, id);
    mvc.perform(
            patch("/api/book-notes/" + id)
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"note\":\"Stolen\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/book-notes/" + id).header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void chapterMustBelongToBook() throws Exception {
    assertEquals(404, note("nonexistent-chapter").getResponse().getStatus());
  }

  @Test
  void shelfPutIsIdempotent() throws Exception {
    String url = "/api/users/me/library/books/" + book();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              put(url)
                  .header("Authorization", auth())
                  .contentType("application/json")
                  .content("{\"shelfState\":\"reading\"}"))
          .andExpect(status().isOk());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM library_entries WHERE user_id=?", Integer.class, user));
    mvc.perform(get("/api/users/me/library/books").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(delete(url).header("Authorization", auth())).andExpect(status().isNoContent());
  }

  @Test
  void progressUsesMysqlUpsert() throws Exception {
    String url = "/api/books/" + book() + "/chapters/" + chapter() + "/progress";
    String input =
        "{\"currentParagraphIndex\":1,\"completedParagraphs\":2,\"totalParagraphs\":2,\"totalReadingMs\":5000}";
    for (int i = 0; i < 2; i++)
      mvc.perform(
              put(url)
                  .header("Authorization", auth())
                  .contentType("application/json")
                  .content(input))
          .andExpect(status().isOk());
    mvc.perform(get(url).header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalReadingMs").value(5000))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM reading_progress WHERE user_id=?", Integer.class, user));
  }

  @Test
  void invalidPageAndUnknownFieldsRejected() throws Exception {
    mvc.perform(get("/api/books?page=0").header("Authorization", auth()))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get("/api/books?pageSize=1000").header("Authorization", auth()))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/book-notes")
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"userId\":\"someone-else\"}"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void duplicateEmailRejected() throws Exception {
    String email = db.queryForObject("SELECT email FROM users WHERE id=?", String.class, user);
    mvc.perform(
            post("/api/users")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "displayName",
                            "Duplicate",
                            "password",
                            "Password123!"))))
        .andExpect(status().isConflict());
  }

  @Test
  void corsAllowsOnlyConfiguredOrigin() throws Exception {
    mvc.perform(
            options("/api/books")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/books")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk());
  }

  @Test
  void missingResourceAndErrorDispatchDoNotBecomeAuthenticationFailures() throws Exception {
    mvc.perform(get("/api/not-yet-migrated").header("Authorization", auth()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/not-yet-migrated")
                .with(request -> {
                  request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                  return request;
                }))
        .andExpect(status().isNotFound());
  }
}
