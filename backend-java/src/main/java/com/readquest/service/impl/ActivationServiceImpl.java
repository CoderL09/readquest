package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ActivationServiceImpl implements ActivationService {
  private final ActivationMapper mapper;
  private final UserMapper users;
  private final MembershipService limits;
  private final AccountWriteGuard guard;
  private final ActivationMail mail;
  private final boolean enabled;
  private final long dailyAi;
  private final int dailyImages;
  private final byte[] encryptionKey;
  private final SecureRandom random = new SecureRandom();

  public ActivationServiceImpl(
      ActivationMapper mapper,
      UserMapper users,
      MembershipService limits,
      AccountWriteGuard guard,
      ActivationMail mail,
      @Value("${readquest.simulated-purchases.enabled:false}") boolean enabled,
      @Value("${readquest.local-development:false}") boolean local,
      @Value("${readquest.jwt-secret}") String secret,
      @Value("${PRO_DAILY_AI_REQUEST_LIMIT:20}") String dailyAi,
      @Value("${SCENE_DAILY_IMAGE_LIMIT:2}") int dailyImages) {
    if (enabled && !local)
      throw new IllegalArgumentException("Simulated purchases require local development mode");
    this.mapper = mapper;
    this.users = users;
    this.limits = limits;
    this.guard = guard;
    this.mail = mail;
    this.enabled = enabled;
    this.dailyAi = dailyAi.isBlank() ? 20 : Long.parseLong(dailyAi);
    this.dailyImages = Math.max(0, dailyImages);
    try {
      encryptionKey =
          MessageDigest.getInstance("SHA-256")
              .digest(("readquest-activation-outbox:" + secret).getBytes(StandardCharsets.UTF_8));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private void enabled() {
    if (!enabled) throw new ApiException(404, "Simulated purchases are disabled");
  }

  private String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private String seal(String id, String code) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(encryptionKey, "AES"),
          new GCMParameterSpec(128, nonce));
      cipher.updateAAD(id.getBytes(StandardCharsets.US_ASCII));
      return Base64.getEncoder().encodeToString(nonce)
          + "."
          + Base64.getEncoder()
              .encodeToString(cipher.doFinal(code.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private String unseal(String id, String sealed) {
    try {
      String[] parts = sealed.split("\\.");
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(encryptionKey, "AES"),
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      cipher.updateAAD(id.getBytes(StandardCharsets.US_ASCII));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.US_ASCII);
    } catch (Exception error) {
      throw new IllegalStateException("Activation payload could not be decrypted");
    }
  }

  private String code() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    StringBuilder result = new StringBuilder("RQ");
    for (int group = 0; group < 3; group++) {
      result.append('-');
      for (int i = 0; i < 4; i++) result.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return result.toString();
  }

  private Map<String, Object> view(ActivationPurchase p) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", p.getId());
    result.put("email", p.getEmail());
    result.put("status", p.getStatus());
    result.put("deliveryStatus", p.getDeliveryStatus());
    result.put("deliveryMode", p.getDeliveryMode());
    result.put("createdAt", p.getCreatedAt());
    result.put("codeExpiresAt", p.getCodeExpiresAt());
    result.put("membershipDays", 7);
    return result;
  }

  public Map<String, Object> settings() {
    return Map.of(
        "enabled",
        enabled,
        "simulated",
        true,
        "mailConfigured",
        mail.configured(),
        "deliveryMode",
        mail.mode(),
        "membershipDays",
        7,
        "purchasesPerDay",
        1,
        "aiRequestsPerDay",
        dailyAi,
        "imagesPerDay",
        dailyImages);
  }

  public Map<String, Object> create(String user, String rawEmail, String requestKey) {
    enabled();
    if (!mail.configured())
      throw new ApiException(
          503, "MAIL_NOT_CONFIGURED", "Configure SMTP or local mail preview first");
    if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{8,128}"))
      throw new ApiException(422, "Invalid Idempotency-Key");
    String email = rawEmail.strip().toLowerCase(Locale.ROOT);
    return guard.execute(
        user,
        () -> {
          var previous = mapper.idempotent(user, requestKey);
          if (previous != null) {
            if (!email.equals(previous.getEmail()))
              throw new ApiException(409, "Idempotency-Key was used with another email");
            return view(previous);
          }
          var account = users.findById(user);
          if (!email.equals(account.getEmail().strip().toLowerCase(Locale.ROOT)))
            throw new ApiException(422, "Use the email registered to the current account");
          if (mapper.recent(user, hash(email), Instant.now().minusSeconds(86400)) >= 1)
            throw new ApiException(
                429,
                "PURCHASE_LIMIT_REACHED",
                "Each account and email can make one simulated purchase every 24 hours");
          var p = new ActivationPurchase();
          p.setId(UUID.randomUUID().toString());
          p.setUserId(user);
          p.setEmail(email);
          p.setEmailHash(hash(email));
          p.setRequestKey(requestKey);
          p.setStatus("pending");
          p.setDeliveryStatus("not_requested");
          p.setDeliveryMode(mail.mode());
          p.setCreatedAt(Instant.now());
          p.setUpdatedAt(p.getCreatedAt());
          mapper.insert(p);
          return view(p);
        });
  }

  public Map<String, Object> confirm(String user, String id) {
    enabled();
    return guard.execute(
        user,
        () -> {
          var p = owned(user, id, true);
          if ("paid".equals(p.getStatus())) return view(p);
          if (!p.getCreatedAt().plusSeconds(1800).isAfter(Instant.now()))
            throw new ApiException(409, "Simulated order has expired");
          if (!mail.configured() || !mail.mode().equals(p.getDeliveryMode()))
            throw new ApiException(
                503, "Mail configuration changed; restore the delivery mode before confirming");
          String code = code(), codeId = UUID.randomUUID().toString();
          Instant expires = Instant.now().plusSeconds(86400);
          mapper.insertCode(codeId, hash(code), code.substring(code.length() - 4), user, expires);
          p.setCodeId(codeId);
          p.setCodeExpiresAt(expires);
          p.setSealedCode(seal(id, code));
          p.setStatus("paid");
          p.setDeliveryStatus("pending");
          p.setNextAttemptAt(Instant.now());
          mapper.save(p);
          return view(p);
        });
  }

  private ActivationPurchase owned(String user, String id, boolean locked) {
    var p = locked ? mapper.lock(id) : mapper.find(user, id);
    if (p == null || !user.equals(p.getUserId())) throw new ApiException(404, "Purchase not found");
    return p;
  }

  public Map<String, Object> get(String user, String id) {
    return guard.execute(user, () -> view(owned(user, id, false)));
  }

  public List<Map<String, Object>> list(String user) {
    return guard.execute(user, () -> mapper.list(user).stream().map(this::view).toList());
  }

  public Map<String, Object> retry(String user, String id) {
    enabled();
    return guard.execute(
        user,
        () -> {
          var p = owned(user, id, true);
          if (!"paid".equals(p.getStatus())
              || p.getSealedCode() == null
              || !p.getCodeExpiresAt().isAfter(Instant.now()))
            throw new ApiException(409, "No valid activation email to resend");
          if (p.getLeaseUntil() != null && p.getLeaseUntil().isAfter(Instant.now())) return view(p);
          if ("pending".equals(p.getDeliveryStatus())) return view(p);
          if (!limits.rateLimit("activation-resend", id, 2, 86400))
            throw new ApiException(429, "Email resend limit reached");
          p.setDeliveryStatus("pending");
          p.setAttempts(0);
          p.setLeaseId(null);
          p.setLeaseUntil(null);
          p.setNextAttemptAt(Instant.now());
          mapper.save(p);
          return view(p);
        });
  }

  @Scheduled(
      initialDelayString = "${readquest.mail.worker-delay-ms:5000}",
      fixedDelayString = "${readquest.mail.worker-delay-ms:5000}")
  public void deliver() {
    if (!enabled) return;
    mapper.expireExhausted();
    for (var candidate : mapper.pending()) {
      String lease = UUID.randomUUID().toString();
      try {
        // Only claiming and finalizing hold database locks. SMTP never runs in a transaction.
        var claimed =
            guard.execute(
                candidate.getUserId(),
                () -> {
                  if (mapper.claim(candidate.getId(), lease, Instant.now().plusSeconds(120)) != 1)
                    return null;
                  return owned(candidate.getUserId(), candidate.getId(), false);
                });
        if (claimed == null) continue;
        String status = "sent";
        Instant next = null;
        try {
          if (!mail.mode().equals(claimed.getDeliveryMode()))
            throw new IllegalStateException("Delivery mode changed");
          mail.send(
              claimed.getId(),
              claimed.getEmail(),
              unseal(claimed.getId(), claimed.getSealedCode()));
        } catch (RuntimeException failure) {
          status = "failed";
          next = Instant.now().plusSeconds(Math.min(3600, 30L << claimed.getAttempts()));
        }
        String outcome = status;
        Instant retryAt = next;
        guard.execute(
            claimed.getUserId(), () -> mapper.finish(claimed.getId(), lease, outcome, retryAt));
      } catch (RuntimeException unavailable) {
        // An unfinalized claim becomes eligible after its lease expires. No private payloads
        // logged.
      }
    }
    for (var expired : mapper.expired())
      try {
        mail.removePreview(expired.getId());
        mapper.clearExpiredPayloads(expired.getId());
      } catch (RuntimeException unavailable) {
        /* Retry preview cleanup later. */
      }
  }

  public void cleanup(String user) {
    mapper.revokeOwnedCodes(user);
    for (; ; ) {
      var rows = mapper.list(user);
      if (rows.isEmpty()) return;
      for (var p : rows) {
        mail.removePreview(p.getId());
        mapper.deleteOne(p.getId());
      }
    }
  }
}
