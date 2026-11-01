package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"readquest.local-development=true", "readquest.simulated-purchases.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivationIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry props) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      props.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:activation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired ActivationService service;
  @MockitoBean ActivationMail mail;
  String user, token, email;
  Map<String, String> delivered = new HashMap<>();

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
                                "displayName",
                                "Activation Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @BeforeEach
  void setup() throws Exception {
    var auth = register();
    user = auth.path("user").path("id").asText();
    email = auth.path("user").path("email").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    when(mail.configured()).thenReturn(true);
    when(mail.mode()).thenReturn("local");
    doAnswer(
            call -> {
              delivered.put(call.getArgument(0), call.getArgument(2));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
  }

  JsonNode create(String key) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/activation-purchases")
                    .header("Authorization", token)
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  String paid() throws Exception {
    String id = create("test-purchase-key").path("id").asText();
    mvc.perform(
            put("/api/activation-purchases/" + id + "/simulated-payment")
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("paid"));
    return id;
  }

  @Test
  void confirmationAndRetriesIssueOnlyOneAccountBoundCode() throws Exception {
    String id = paid();
    assertEquals(id, create("test-purchase-key").path("id").asText());
    mvc.perform(
            put("/api/activation-purchases/" + id + "/simulated-payment")
                .header("Authorization", token))
        .andExpect(status().isOk());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM membership_redemption_codes WHERE bound_user_id=?",
            Integer.class,
            user));
    service.deliver();
    String code = delivered.get(id);
    assertNotNull(code);
    assertTrue(code.matches("RQ-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"));
    assertFalse(
        db.queryForObject(
                "SELECT sealed_code FROM activation_purchases WHERE id=?", String.class, id)
            .contains(code));
    var other = register();
    mvc.perform(
            post("/api/users/me/membership-redemptions")
                .header("Authorization", "Bearer " + other.path("accessToken").asText())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("code", code))))
        .andExpect(status().isConflict());
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/users/me/membership-redemptions")
                  .header("Authorization", token)
                  .contentType("application/json")
                  .content(json.writeValueAsString(Map.of("code", code))))
          .andExpect(i == 0 ? status().isCreated() : status().isConflict());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_entitlements WHERE user_id=?", Integer.class, user));
    String response =
        mvc.perform(get("/api/activation-purchases/" + id).header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(response.contains("sealedCode"));
    assertFalse(response.contains(code));
  }

  @Test
  void enforcesPurchaseLimitAndRegisteredEmail() throws Exception {
    mvc.perform(
            post("/api/activation-purchases")
                .header("Authorization", token)
                .header("Idempotency-Key", "test-purchase-key")
                .contentType("application/json")
                .content("{\"email\":\"other@example.test\"}"))
        .andExpect(status().isUnprocessableEntity());
    create("test-purchase-key");
    mvc.perform(
            post("/api/activation-purchases")
                .header("Authorization", token)
                .header("Idempotency-Key", "second-purchase-key")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void failedMailCanRetryWithoutIssuingAnotherCode() throws Exception {
    String id = paid();
    doThrow(new IllegalStateException("simulated SMTP failure"))
        .when(mail)
        .send(anyString(), anyString(), anyString());
    service.deliver();
    assertEquals("failed", service.get(user, id).get("deliveryStatus"));
    doAnswer(
            call -> {
              delivered.put(call.getArgument(0), call.getArgument(2));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
    service.retry(user, id);
    service.deliver();
    assertEquals("sent", service.get(user, id).get("deliveryStatus"));
    assertNotNull(delivered.get(id));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM membership_redemption_codes WHERE bound_user_id=?",
            Integer.class,
            user));
  }

  @Test
  void otherUsersCannotReadOrConfirmPurchases() throws Exception {
    String id = create("test-purchase-key").path("id").asText();
    var other = register();
    String auth = "Bearer " + other.path("accessToken").asText();
    mvc.perform(get("/api/activation-purchases/" + id).header("Authorization", auth))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/activation-purchases/" + id + "/simulated-payment")
                .header("Authorization", auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void unconfiguredMailDoesNotCreateOrdersOrCodes() throws Exception {
    when(mail.configured()).thenReturn(false);
    mvc.perform(
            post("/api/activation-purchases")
                .header("Authorization", token)
                .header("Idempotency-Key", "test-purchase-key")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isServiceUnavailable());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM activation_purchases WHERE user_id=?", Integer.class, user));
  }

  @Test
  void deletionRevokesUnusedCodesAndRemovesPrivateOutbox() throws Exception {
    String id = paid();
    mvc.perform(
            post("/api/users/me/deletion-requests")
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"password\":\"Password123!\",\"confirmation\":\"DELETE\"}"))
        .andExpect(status().isAccepted());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM activation_purchases WHERE user_id=?", Integer.class, user));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM membership_redemption_codes WHERE bound_user_id=?",
            Integer.class,
            user));
    verify(mail).removePreview(id);
  }

  @Test
  void expiryErasesDeliveryPayloadAndPreview() throws Exception {
    String id = paid();
    db.update(
        "UPDATE activation_purchases SET code_expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE"
            + " id=?",
        id);
    service.deliver();
    assertNull(
        db.queryForObject(
            "SELECT sealed_code FROM activation_purchases WHERE id=?", String.class, id));
    assertEquals("expired", service.get(user, id).get("deliveryStatus"));
    verify(mail).removePreview(id);
    verify(mail, never()).send(anyString(), anyString(), anyString());
  }

  @Autowired MembershipService memberships;
  @Autowired com.readquest.mapper.ActivationMapper activationMapper;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Test
  void smtpRunsOutsideDatabaseTransactionAndConcurrentWorkersSendOnce() throws Exception {
    String id = paid();
    var sends = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            call -> {
              assertFalse(
                  org.springframework.transaction.support.TransactionSynchronizationManager
                      .isActualTransactionActive());
              sends.incrementAndGet();
              Thread.sleep(150);
              return null;
            })
        .when(mail)
        .send(eq(id), anyString(), anyString());
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
      var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
      for (int i = 0; i < 6; i++)
        tasks.add(() -> {
          service.deliver();
          return null;
        });
      for (var result : pool.invokeAll(tasks)) result.get();
    }
    assertEquals(1, sends.get());
    assertEquals("sent", service.get(user, id).get("deliveryStatus"));
    assertNull(
        db.queryForObject(
            "SELECT lease_id FROM activation_purchases WHERE id=?", String.class, id));
  }

  @Test
  void expiredClaimIsRecoveredAndStaleWorkerCannotFinalizeNewClaim() throws Exception {
    String id = paid();
    String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
    assertEquals(1, activationMapper.claim(id, first, java.time.Instant.now().plusSeconds(120)));
    assertEquals(0, activationMapper.claim(id, second, java.time.Instant.now().plusSeconds(120)));
    db.update(
        "UPDATE activation_purchases SET lease_until=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
        id);
    assertEquals(1, activationMapper.claim(id, second, java.time.Instant.now().plusSeconds(120)));
    assertEquals(0, activationMapper.finish(id, first, "sent", null));
    assertEquals(1, activationMapper.finish(id, second, "sent", null));
    assertEquals(
        2,
        db.queryForObject(
            "SELECT attempts FROM activation_purchases WHERE id=?", Integer.class, id));
  }

  @Test
  void simultaneousRedemptionGrantsExactlyOneEntitlement() throws Exception {
    String id = paid();
    service.deliver();
    String code = delivered.get(id);
    var successes = new java.util.concurrent.atomic.AtomicInteger();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
      for (int i = 0; i < 24; i++)
        tasks.add(() -> {
          try {
            memberships.redeem(user, code);
            successes.incrementAndGet();
          } catch (com.readquest.exception.ApiException conflict) {
            assertEquals(409, conflict.status());
          }
          return null;
        });
      for (var result : pool.invokeAll(tasks)) result.get();
    }
    assertEquals(1, successes.get());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_entitlements WHERE user_id=?", Integer.class, user));
  }

  @Test
  void redemptionRollsBackCodeConsumptionAndEntitlementTogether() throws Exception {
    String id = paid();
    service.deliver();
    String code = delivered.get(id);
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    assertThrows(
        IllegalStateException.class,
        () ->
            tx.execute(status -> {
              memberships.redeem(user, code);
              throw new IllegalStateException("synthetic rollback");
            }));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_entitlements WHERE user_id=?", Integer.class, user));
    assertNull(
        db.queryForObject(
            "SELECT redeemed_at FROM membership_redemption_codes WHERE bound_user_id=?",
            java.sql.Timestamp.class,
            user));
    assertNotNull(memberships.redeem(user, code));
  }

  @Test
  void finalAbandonedClaimBecomesTerminalAndCanBeExplicitlyRetried() throws Exception {
    String id = paid();
    db.update(
        "UPDATE activation_purchases SET attempts=5,lease_id=?,lease_until=? WHERE id=?",
        UUID.randomUUID().toString(),
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
        id);
    service.deliver();
    assertEquals("failed", service.get(user, id).get("deliveryStatus"));
    verify(mail, never()).send(eq(id), anyString(), anyString());
    service.retry(user, id);
    service.deliver();
    assertEquals("sent", service.get(user, id).get("deliveryStatus"));
  }
}
