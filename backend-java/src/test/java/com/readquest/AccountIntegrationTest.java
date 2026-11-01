package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AccountService accounts;
  String token, user;

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
                                "Data Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = auth.path("accessToken").asText();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,asset_scope,created_at,updated_at)"
            + " VALUES(?,?,'Account"
            + " Book','completed','private',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user);
    db.update(
        "INSERT INTO chapters(id,book_id,chapter_index,stage_title,content) VALUES(?,?,0,'One','A"
            + " reader watched the river.')",
        user,
        user);
    db.update(
        "INSERT INTO book_notes(id,user_id,book_id,quote,note,created_at,updated_at)"
            + " VALUES(?,?,?,'river','private note',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        user,
        user);
  }

  @Test
  void exportIsUserScopedAndContainsNoCredentialOrStorageFields() throws Exception {
    var output = new ByteArrayOutputStream();
    accounts.export(user, token).writeTo(output);
    var value = json.readTree(output.toByteArray());
    assertEquals("readquest-account-export", value.path("format").asText());
    assertEquals(1, value.path("data").path("notes").size());
    assertEquals("private note", value.path("data").path("notes").get(0).path("note").asText());
    String text = output.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertFalse(text.contains("passwordHash"));
    assertFalse(text.contains("tokenHash"));
    assertFalse(text.contains("filePath"));
    assertFalse(text.contains("export_cursor"));
    String previous = user;
    setup();
    output.reset();
    accounts.export(user, token).writeTo(output);
    assertFalse(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains(previous));
  }

  @Test
  void exportReauthorizesBeforeEveryOutputChunk() throws Exception {
    var output =
        new ByteArrayOutputStream() {
          boolean revoked;

          @Override
          public synchronized void write(byte[] bytes, int offset, int length) {
            super.write(bytes, offset, length);
            if (!revoked) {
              revoked = true;
              db.update(
                  "UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?", user);
            }
          }
        };
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> accounts.export(user, token).writeTo(output));
    assertFalse(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains("private note"));
  }

  @Test
  void deletionVerifiesPasswordRevokesSessionsAndErasesPrivateRows() throws Exception {
    mvc.perform(
            post("/api/users/me/deletion-requests")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"confirmation\":\"DELETE\",\"password\":\"WrongPassword!\"}"))
        .andExpect(status().isForbidden());
    assertEquals(
        "active", db.queryForObject("SELECT status FROM users WHERE id=?", String.class, user));
    mvc.perform(
            post("/api/users/me/deletion-requests")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"confirmation\":\"DELETE\",\"password\":\"Password123!\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.deletion.status").value("completed"));
    assertEquals(
        "deleted", db.queryForObject("SELECT status FROM users WHERE id=?", String.class, user));
    assertEquals(
        0,
        db.queryForObject("SELECT COUNT(*) FROM book_notes WHERE user_id=?", Integer.class, user));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM refresh_sessions WHERE user_id=?", Integer.class, user));
    assertNull(db.queryForObject("SELECT owner_id FROM books WHERE id=?", String.class, user));
    assertEquals(
        "removed",
        db.queryForObject("SELECT visibility FROM books WHERE id=?", String.class, user));
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void failedFileCleanupRemainsPendingAndWorkerCanRetry() throws Exception {
    Path root = Path.of("target/test-avatar-files").toAbsolutePath().normalize();
    String name = UUID.randomUUID() + ".png";
    Path directory = root.resolve(name);
    Files.createDirectories(directory);
    Path blocker = directory.resolve("keep");
    Files.writeString(blocker, "test");
    db.update("UPDATE users SET avatar_url=? WHERE id=?", "/uploads/avatars/" + name, user);
    try {
      assertEquals("pending", accounts.requestDeletion(user, "Password123!").get("status"));
      assertEquals(
          "deletion_pending",
          db.queryForObject("SELECT status FROM users WHERE id=?", String.class, user));
      Files.delete(blocker);
      accounts.processDeletions();
      assertEquals(
          "completed",
          db.queryForObject(
              "SELECT status FROM account_deletion_jobs WHERE user_id=?", String.class, user));
      assertFalse(Files.exists(directory));
    } finally {
      Files.deleteIfExists(blocker);
      Files.deleteIfExists(directory);
    }
  }
}
