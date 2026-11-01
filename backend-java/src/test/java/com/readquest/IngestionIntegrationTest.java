package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.mapper.IngestionMapper;
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
class IngestionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired IngestionService imports;
  @Autowired IngestionMapper mapper;
  @Autowired JdbcTemplate db;
  String user, token;

  @BeforeEach
  void register() throws Exception {
    var response =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.com",
                                "displayName",
                                "Importer",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var body = json.readTree(response.getResponse().getContentAsString());
    user = body.path("user").path("id").asText();
    token = body.path("accessToken").asText();
  }

  String auth() {
    return "Bearer " + token;
  }

  private IngestionService.Admission upload() {
    return imports.upload(
        user,
        new MockMultipartFile(
            "file",
            "fixture.txt",
            "text/plain",
            ("Hello world.\n\nThis is a test. " + user).getBytes(StandardCharsets.UTF_8)),
        true);
  }

  @Test
  void uploadProcessingReadingAndOwnerDownload() throws Exception {
    var admitted = upload();
    assertEquals("queued", admitted.job().getStatus());
    imports.processPending();
    assertEquals("completed", imports.get(user, admitted.job().getId()).getStatus());
    mvc.perform(
            get("/api/books/" + admitted.book().getId() + "/chapters")
                .header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").isNotEmpty());
    mvc.perform(
            get("/api/books/" + admitted.book().getId() + "/source")
                .header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/octet-stream"));
  }

  @Test
  void questUploadPersistsModeAndCreatesCompleteReadingStages() {
    String text = "A river flowed through the quiet valley. ".repeat(180);
    var admitted =
        imports.upload(
            user,
            new MockMultipartFile(
                "file", "quest.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8)),
            true,
            "quest",
            200);
    assertEquals("quest", admitted.book().getReadingMode());
    assertEquals(200, admitted.job().getWordsPerQuest());
    imports.processPending();
    assertEquals("completed", imports.get(user, admitted.job().getId()).getStatus());
    var contents =
        db.queryForList(
            "SELECT content FROM chapters WHERE book_id=? ORDER BY chapter_index",
            String.class,
            admitted.book().getId());
    assertTrue(contents.size() > 2);
    assertEquals(text.strip(), String.join(" ", contents));
    assertEquals(
        "Continue reading this part of the text.",
        db.queryForObject(
            "SELECT stage_summary FROM chapters WHERE book_id=? ORDER BY chapter_index LIMIT 1",
            String.class,
            admitted.book().getId()));
  }

  @Test
  void duplicateUploadDoesNotCreateMoreJobs() {
    var first = upload();
    var second = upload();
    assertTrue(second.duplicate());
    assertEquals(first.book().getId(), second.book().getId());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM book_import_jobs WHERE user_id=?", Integer.class, user));
  }

  @Test
  void cancellingRevokesTheLeaseAndRetryResetsTheBook() {
    var admission = upload();
    String id = admission.job().getId();
    mapper.claim(id, "cancelled-run");
    assertEquals(1, mapper.renew(id, "cancelled-run"));
    assertEquals(0, mapper.renew(id, "other-run"));
    assertEquals("cancelled", imports.cancel(user, id).getStatus());
    assertEquals("cancelled", imports.cancel(user, id).getStatus());
    assertEquals(0, mapper.renew(id, "cancelled-run"));
    assertNull(mapper.lockClaim(id, "cancelled-run"));
    imports.processPending();
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM chapters WHERE book_id=?",
            Integer.class,
            admission.book().getId()));
    var retry = imports.retry(user, admission.book().getId());
    assertNotEquals(id, retry.getId());
    assertEquals(
        "uploaded",
        db.queryForObject(
            "SELECT status FROM books WHERE id=?", String.class, admission.book().getId()));
    assertEquals(retry.getId(), imports.retry(user, admission.book().getId()).getId());
    imports.processPending();
    assertEquals("completed", imports.get(user, retry.getId()).getStatus());
    assertEquals(100, imports.get(user, retry.getId()).getProgress());
    assertEquals("completed", imports.get(user, retry.getId()).getPhase());
    assertEquals("completed", imports.cancel(user, retry.getId()).getStatus());
  }

  @Test
  void oldWorkersCannotReportProgressAfterRecovery() {
    var admission = upload();
    String id = admission.job().getId();
    mapper.claim(id, "old-run");
    db.update(
        "UPDATE book_import_jobs SET lease_expires_at=TIMESTAMPADD(MINUTE,-11,CURRENT_TIMESTAMP) WHERE id=?",
        id);
    assertEquals(0, mapper.renew(id, "old-run"));
    assertEquals(1, mapper.claim(id, "new-run"));
    assertEquals(0, mapper.checkpoint(id, "old-run", "persisting", 80));
    assertEquals(0, mapper.finishJob(id, "old-run"));
    assertEquals(1, mapper.checkpoint(id, "new-run", "segmenting", 45));
    assertEquals(45, imports.get(user, id).getProgress());
  }

  @Test
  void claimIsExclusiveAndFenced() {
    var admission = upload();
    String run = UUID.randomUUID().toString();
    assertEquals(1, mapper.claim(admission.job().getId(), run));
    assertEquals(0, mapper.claim(admission.job().getId(), "other"));
    assertNull(mapper.lockClaim(admission.job().getId(), "other"));
    assertNotNull(mapper.lockClaim(admission.job().getId(), run));
  }

  @Test
  void failedParsingIsDurable() {
    var admission =
        imports.upload(
            user,
            new MockMultipartFile("file", "bad.txt", "text/plain", new byte[] {0, 1, 2}),
            true);
    imports.processPending();
    assertEquals("failed", imports.get(user, admission.job().getId()).getStatus());
    assertEquals(
        "failed",
        db.queryForObject(
            "SELECT status FROM books WHERE id=?", String.class, admission.book().getId()));
  }

  @Test
  void removedBookCannotBePublishedByWorker() throws Exception {
    var admission = upload();
    imports.remove(user, admission.book().getId());
    imports.processPending();
    assertEquals(
        "removed",
        db.queryForObject(
            "SELECT visibility FROM books WHERE id=?", String.class, admission.book().getId()));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM chapters WHERE book_id=?",
            Integer.class,
            admission.book().getId()));
  }

  @Test
  void validatesRightsAndFileSignatures() throws Exception {
    var file =
        new MockMultipartFile(
            "file", "book.pdf", "application/pdf", "Not a PDF".getBytes(StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/api/books")
                .file(file)
                .param("rightsAttested", "true")
                .header("Authorization", auth()))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            multipart("/api/books")
                .file(file)
                .param("rightsAttested", "false")
                .header("Authorization", auth()))
        .andExpect(status().isUnprocessableEntity());
    assertEquals(
        0, db.queryForObject("SELECT COUNT(*) FROM books WHERE owner_id=?", Integer.class, user));
  }

  @Test
  void anotherUserCannotInspectJobsOrDownload() throws Exception {
    var admission = upload();
    String other = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO users(id,email,display_name,password_hash,created_at,updated_at)"
            + " VALUES(?,?,'Other','unused',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        other,
        other + "@example.com");
    db.update("UPDATE book_import_jobs SET user_id=? WHERE id=?", other, admission.job().getId());
    mvc.perform(get("/api/book-imports/" + admission.job().getId()).header("Authorization", auth()))
        .andExpect(status().isNotFound());
    db.update("UPDATE books SET owner_id=? WHERE id=?", other, admission.book().getId());
    mvc.perform(
            get("/api/books/" + admission.book().getId() + "/source")
                .header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void expiredLeaseIsRecovered() {
    var admission = upload();
    mapper.claim(admission.job().getId(), "old-run");
    db.update(
        "UPDATE book_import_jobs SET lease_expires_at=TIMESTAMPADD(MINUTE,-11,CURRENT_TIMESTAMP)"
            + " WHERE id=?",
        admission.job().getId());
    imports.processPending();
    var job = imports.get(user, admission.job().getId());
    assertEquals("completed", job.getStatus());
    assertEquals(2, job.getAttempts());
    assertNull(mapper.lockClaim(job.getId(), "old-run"));
  }

  @Test
  void exhaustedLeaseBecomesFailedInsteadOfStayingStuck() {
    var admission = upload();
    mapper.claim(admission.job().getId(), "old-run");
    db.update(
        "UPDATE book_import_jobs SET"
            + " attempts=3,lease_expires_at=TIMESTAMPADD(MINUTE,-11,CURRENT_TIMESTAMP) WHERE id=?",
        admission.job().getId());
    imports.processPending();
    assertEquals("failed", imports.get(user, admission.job().getId()).getStatus());
  }

  @Test
  void readingEventsAreIdempotentAndStructureUsesSourceText() throws Exception {
    var admission = upload();
    imports.processPending();
    String book = admission.book().getId();
    String chapter =
        db.queryForObject("SELECT id FROM chapters WHERE book_id=?", String.class, book);
    mvc.perform(get("/api/chapters/" + chapter + "/structure").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisSource").value("local-text"))
        .andExpect(jsonPath("$.paragraphs[0].text").value("Hello world."));
    String path = "/api/books/" + book + "/chapters/" + chapter + "/reading-events";
    String event = UUID.randomUUID().toString();
    Map<String, Object> input =
        new HashMap<>(
            Map.of(
                "eventId",
                event,
                "currentParagraphIndex",
                1,
                "completedParagraphs",
                2,
                "totalParagraphs",
                2,
                "readingDurationMs",
                2000));
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(path)
                  .header("Authorization", auth())
                  .contentType("application/json")
                  .content(json.writeValueAsString(input)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalReadingMs").value(2000));
    input.put("readingDurationMs", 999);
    mvc.perform(
            post(path)
                .header("Authorization", auth())
                .contentType("application/json")
                .content(json.writeValueAsString(input)))
        .andExpect(status().isConflict());
    input.put("eventId", UUID.randomUUID().toString());
    input.put("readingDurationMs", 3000);
    mvc.perform(
            post(path)
                .header("Authorization", auth())
                .contentType("application/json")
                .content(json.writeValueAsString(input)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalReadingMs").value(5000));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM reading_events WHERE user_id=?", Integer.class, user));
  }
}
