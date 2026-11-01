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
class ProfileIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired IngestionService imports;
  String user, token;

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
                                "Profile Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var a = json.readTree(r.getResponse().getContentAsString());
    user = a.path("user").path("id").asText();
    token = a.path("accessToken").asText();
  }

  String auth() {
    return "Bearer " + token;
  }

  @Test
  void threeCalibrationSamplesMatchOriginalScoringAndPersistBands() throws Exception {
    var r =
        mvc.perform(post("/api/calibration-sessions").header("Authorization", auth()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sample.lexile").value(480))
            .andReturn();
    String id = json.readTree(r.getResponse().getContentAsString()).path("sessionId").asText();
    String body =
        "{\"readingSeconds\":30,\"lookupCount\":0,\"comprehensionScore\":0.9,\"flowScore\":0.9}";
    for (int step = 1; step <= 3; step++) {
      var request =
          mvc.perform(
                  post("/api/calibration-sessions/" + id + "/samples/" + step + "/responses")
                      .header("Authorization", auth())
                      .contentType("application/json")
                      .content(body))
              .andExpect(status().isOk());
      if (step == 3)
        request
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.profile.currentLexile").value(600))
            .andExpect(jsonPath("$.profile.iPlusOneMin").value(650));
    }
    assertEquals(
        600,
        db.queryForObject(
            "SELECT current_lexile FROM reading_profiles WHERE user_id=?", Integer.class, user));
    mvc.perform(get("/api/users/me/reading-passport").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.iPlusOneMax").value(750));
  }

  @Test
  void repeatedCalibrationResponseDoesNotAdvanceTwice() throws Exception {
    var r =
        mvc.perform(post("/api/calibration-sessions").header("Authorization", auth())).andReturn();
    String id = json.readTree(r.getResponse().getContentAsString()).path("sessionId").asText(),
        path = "/api/calibration-sessions/" + id + "/samples/1/responses";
    String body =
        "{\"readingSeconds\":30,\"lookupCount\":0,\"comprehensionScore\":0.9,\"flowScore\":0.9}";
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(path)
                  .header("Authorization", auth())
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currentStep").value(2));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM calibration_samples WHERE session_id=?", Integer.class, id));
    mvc.perform(
            post(path)
                .header("Authorization", auth())
                .contentType("application/json")
                .content(body.replace("0.9", "0.5")))
        .andExpect(status().isConflict());
  }

  @Test
  void growthUsesActualReadingTimeAndAdjustmentCooldown() throws Exception {
    var admission =
        imports.upload(
            user,
            new MockMultipartFile(
                "file",
                "growth.txt",
                "text/plain",
                "One.\n\nTwo.\n\nThree.\n\nFour.\n\nFive.".getBytes(StandardCharsets.UTF_8)),
            true);
    imports.processPending();
    String book = admission.book().getId(),
        chapter = db.queryForObject("SELECT id FROM chapters WHERE book_id=?", String.class, book),
        event = UUID.randomUUID().toString();
    String input =
        json.writeValueAsString(
            Map.of(
                "eventId",
                event,
                "currentParagraphIndex",
                4,
                "completedParagraphs",
                5,
                "totalParagraphs",
                5,
                "readingDurationMs",
                60000));
    String endpoint = "/api/books/" + book + "/chapters/" + chapter + "/reading-events";
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(endpoint)
                  .header("Authorization", auth())
                  .contentType("application/json")
                  .content(input))
          .andExpect(status().isOk());
    mvc.perform(get("/api/users/me/growth").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.currentLexile").value(520))
        .andExpect(jsonPath("$.reading.totalReadingMinutes").value(1.0))
        .andExpect(jsonPath("$.reading.activeBook.id").value(book))
        .andExpect(jsonPath("$.flow.quietParagraphs").value(5))
        .andExpect(jsonPath("$.adjustment.lexileDelta").value(0));
  }

  @Test
  void metadataKeepsOriginalSeedRecommendations() throws Exception {
    mvc.perform(get("/api/users/me/recommendations").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recommended.title").value("The Clockmaker's Secret"))
        .andExpect(jsonPath("$.bands.length()").value(5));
  }
}
