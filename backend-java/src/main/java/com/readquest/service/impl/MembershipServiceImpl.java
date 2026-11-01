package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.MembershipMapper;
import com.readquest.pojo.*;
import com.readquest.service.MembershipService;
import com.readquest.vo.AuthResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MembershipServiceImpl implements MembershipService {
  private final MembershipMapper mapper;
  private final TransactionTemplate tx;
  private final Long daily;
  private final com.readquest.infrastructure.RedisBackend redis;
  private static final Set<String> FEATURES =
      Set.of(
          "reader_rag_chat",
          "grammar_analysis",
          "module1_analysis",
          "module2_analysis",
          "scene_image",
          "ai_explanation",
          "ai_grading",
          "advanced_ai_analytics");

  public MembershipServiceImpl(
      MembershipMapper m,
      org.springframework.transaction.PlatformTransactionManager manager,
      org.springframework.beans.factory.ObjectProvider<com.readquest.infrastructure.RedisBackend>
          redis,
      @Value("${PRO_DAILY_AI_REQUEST_LIMIT:}") String limit) {
    mapper = m;
    this.redis = redis.getIfAvailable();
    tx = new TransactionTemplate(manager);
    if (!limit.isBlank() && !limit.matches("[0-9]+"))
      throw new IllegalArgumentException("Invalid PRO_DAILY_AI_REQUEST_LIMIT");
    daily = limit.isBlank() ? 20L : Long.parseLong(limit);
  }

  private String hash(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private User lock(String user) {
    User u = mapper.lockUser(user);
    if (u == null) throw new ApiException(403, "Account is not active");
    return u;
  }

  public List<UserEntitlement> active(String user) {
    return mapper.active(user);
  }

  @Transactional
  public UserEntitlement redeem(String user, String raw) {
    lock(user);
    String code = raw.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    if (!code.matches("RQ-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"))
      throw new ApiException(
          409, "INVALID_OR_USED_CODE", "Membership code is invalid, expired, or used");
    String hashed = hash(code);
    MembershipRedemptionCode redemption = mapper.lockCode(hashed);
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    if (redemption == null
        || (redemption.getBoundUserId() != null && !user.equals(redemption.getBoundUserId()))
        || redemption.getRedeemedAt() != null
        || redemption.getRevokedAt() != null
        || !redemption.getExpiresAt().isAfter(now))
      throw new ApiException(
          409, "INVALID_OR_USED_CODE", "Membership code is invalid, expired, or used");
    UserEntitlement latest = mapper.latest(user);
    if (latest != null && latest.getEndsAt() == null)
      throw new ApiException(
          409, "PERMANENT_MEMBERSHIP_ACTIVE", "Permanent membership is already active");
    Instant starts = latest != null && latest.getEndsAt().isAfter(now) ? latest.getEndsAt() : now;
    UserEntitlement granted = new UserEntitlement();
    granted.setId(UUID.randomUUID().toString());
    granted.setSourceOrderNo("RDM-" + redemption.getId());
    granted.setUserId(user);
    granted.setCode("pro");
    granted.setLabel(redemption.getLabel());
    granted.setStartsAt(starts);
    granted.setEndsAt(
        redemption.getDurationDays() == null
            ? null
            : starts.plusSeconds(redemption.getDurationDays() * 86400L));
    if (mapper.consume(hashed, user) != 1)
      throw new ApiException(409, "INVALID_OR_USED_CODE", "Membership code is already used");
    mapper.insertEntitlement(granted);
    return granted;
  }

  public boolean admit(String user, String feature, String request) {
    if (!FEATURES.contains(feature)) throw new ApiException(422, "Unknown AI feature");
    return Boolean.TRUE.equals(
        tx.execute(s -> {
          lock(user);
          Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
              from =
                  now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
          boolean pro = mapper.active(user).stream().anyMatch(e -> "pro".equals(e.getCode()));
          String result =
              !pro
                  ? "denied_entitlement"
                  : daily != null && mapper.usedUnits(user, from, from.plusSeconds(86400)) >= daily
                      ? "denied_quota"
                      : "allowed";
          FeatureUsage usage = new FeatureUsage();
          usage.setUserId(user);
          usage.setRequestId(request);
          usage.setFeature(feature);
          usage.setOccurredAt(now);
          usage.setResult(result);
          usage.setUnits("allowed".equals(result) ? 1 : 0);
          mapper.record(usage);
          return "allowed".equals(result);
        }));
  }

  public void providerFailed(String user, String request) {
    mapper.providerFailed(user, request);
  }

  public boolean rateLimit(String scope, String identity, int limit, long seconds) {
    if (limit < 1 || seconds < 1) throw new IllegalArgumentException("Invalid rate limit");
    long now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).getEpochSecond(),
        window = now / seconds;
    String key = hash(scope + ":" + identity);
    long expires = (window + 1) * seconds;
    if (redis != null
        && redis
            .allow("rq:rate:v1:" + key + ":" + window, limit, expires * 1000)
            .filter(allowed -> !allowed)
            .isPresent()) return false;
    // Every admitted request is counted durably. Redis failure/restart cannot reset the budget.
    return Boolean.TRUE.equals(
        tx.execute(status -> {
          mapper.hit(key, window, Instant.ofEpochSecond(expires));
          return mapper.hits(key) <= limit;
        }));
  }

  @Transactional
  public AuthResponse.PublicUser updateName(String user, String name) {
    User u = lock(user);
    String value = name.strip();
    if (value.isEmpty()
        || value.length() > 50
        || value.codePoints().anyMatch(c -> c <= 31 || (c >= 127 && c <= 159)))
      throw new ApiException(422, "Invalid display name");
    mapper.updateDisplayName(user, value);
    u.setDisplayName(value);
    return AuthResponse.PublicUser.of(u);
  }

  public List<RefreshSession> sessions(String user) {
    return mapper.sessions(user);
  }

  @Transactional
  public void revokeSession(String user, String id) {
    lock(user);
    if (mapper.revokeSession(user, id) != 1) throw new ApiException(404, "Session not found");
  }
}
