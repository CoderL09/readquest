package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.MembershipService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired MembershipService service;
  String user, token;

  @BeforeEach
  void register() throws Exception {
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
                                "Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var body = json.readTree(result.getResponse().getContentAsString());
    user = body.path("user").path("id").asText();
    token = body.path("accessToken").asText();
  }

  String auth() {
    return "Bearer " + token;
  }

  String code(Integer days) throws Exception {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    StringBuilder value = new StringBuilder("RQ");
    var random = new java.security.SecureRandom();
    for (int g = 0; g < 3; g++) {
      value.append('-');
      for (int i = 0; i < 4; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    String plain = value.toString();
    String hash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(plain.getBytes(StandardCharsets.UTF_8)));
    db.update(
        "INSERT INTO"
            + " membership_redemption_codes(id,code_hash,code_suffix,label,duration_days,expires_at)"
            + " VALUES(?,?,?,'ReadQuest Pro',?,TIMESTAMPADD(DAY,1,CURRENT_TIMESTAMP))",
        UUID.randomUUID().toString(),
        hash,
        plain.substring(plain.length() - 4),
        days);
    return plain;
  }

  @Test
  void redeemOnceAndExtendExistingMembership() throws Exception {
    String code = code(30);
    var first = service.redeem(user, code);
    assertEquals("pro", first.getCode());
    assertEquals(
        30 * 86400L,
        java.time.Duration.between(first.getStartsAt(), first.getEndsAt()).getSeconds());
    assertThrows(com.readquest.exception.ApiException.class, () -> service.redeem(user, code));
    var second = service.redeem(user, code(30));
    assertEquals(first.getEndsAt().toEpochMilli(), second.getStartsAt().toEpochMilli());
    assertEquals(1, service.active(user).size());
  }

  @Test
  void permanentMembershipDoesNotConsumeAnotherCode() throws Exception {
    service.redeem(user, code(null));
    String unused = code(30);
    assertThrows(com.readquest.exception.ApiException.class, () -> service.redeem(user, unused));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM membership_redemption_codes WHERE redeemed_by_user_id=?",
            Integer.class,
            user));
  }

  @Test
  void admissionRequiresMembershipAndRecordsDenial() {
    assertFalse(service.admit(user, "grammar_analysis", UUID.randomUUID().toString()));
    assertEquals(
        "denied_entitlement",
        db.queryForObject("SELECT result FROM feature_usage WHERE user_id=?", String.class, user));
  }

  @Test
  void admissionAndFailureArePersisted() throws Exception {
    service.redeem(user, code(30));
    String request = UUID.randomUUID().toString();
    assertTrue(service.admit(user, "grammar_analysis", request));
    service.providerFailed(user, request);
    assertEquals(
        "provider_failed",
        db.queryForObject("SELECT result FROM feature_usage WHERE user_id=?", String.class, user));
  }

  @Test
  void defaultDailyQuotaStopsAtTwentyAndFailedRequestsReleaseQuota() throws Exception {
    service.redeem(user, code(7));
    String first = UUID.randomUUID().toString();
    assertTrue(service.admit(user, "ai_explanation", first));
    for (int i = 1; i < 20; i++)
      assertTrue(service.admit(user, "ai_explanation", UUID.randomUUID().toString()));
    assertFalse(service.admit(user, "ai_explanation", UUID.randomUUID().toString()));
    service.providerFailed(user, first);
    assertTrue(service.admit(user, "ai_explanation", UUID.randomUUID().toString()));
  }

  @Test
  void simulationIsDisabledWithoutLocalProfile() throws Exception {
    mvc.perform(get("/api/membership-offers").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mvc.perform(
            put("/api/activation-purchases/" + UUID.randomUUID() + "/simulated-payment")
                .header("Authorization", auth()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rateLimitCountsExactlyAndUsesIndependentIdentities() {
    assertTrue(service.rateLimit("test", user, 2, 3600));
    assertTrue(service.rateLimit("test", user, 2, 3600));
    assertFalse(service.rateLimit("test", user, 2, 3600));
    assertTrue(service.rateLimit("test", user + "other", 2, 3600));
  }

  @Test
  void profileRejectsControlCharactersAndSessionListHidesSecrets() throws Exception {
    mvc.perform(
            patch("/api/users/me")
                .header("Authorization", auth())
                .contentType("application/json")
                .content("{\"displayName\":\"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.displayName").value("New Name"));
    mvc.perform(
            patch("/api/users/me")
                .header("Authorization", auth())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", "Bad\u0001Name"))))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get("/api/users/me/sessions").header("Authorization", auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessions[0].tokenHash").doesNotExist())
        .andExpect(jsonPath("$.sessions[0].createdAt").isNotEmpty());
  }

  @Test
  void articleSseFallbackPreservesWireFormatAndOriginalText() throws Exception {
    var request =
        mvc.perform(
                post("/api/article-analyses")
                    .header("Authorization", auth())
                    .contentType("application/json")
                    .content("{\"text\":\"Hello world.\\n\\nSecond paragraph.\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();
    request.getAsyncResult(10000);
    mvc.perform(asyncDispatch(request))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data: [DONE]")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello world.")));
  }
}
