package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenServiceImpl implements TokenService {
  private final byte[] key;
  private final ObjectMapper json;
  private final UserMapper users;
  private final SessionMapper sessions;

  public TokenServiceImpl(
      @Value("${readquest.jwt-secret}") String secret,
      ObjectMapper json,
      UserMapper users,
      SessionMapper sessions) {
    if (secret.trim().length() < 32)
      throw new IllegalArgumentException("JWT_SECRET must contain at least 32 characters");
    this.key = digest(("access:" + secret.trim()).getBytes(StandardCharsets.UTF_8));
    this.json = json;
    this.users = users;
    this.sessions = sessions;
  }

  private byte[] digest(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String hash(String value) {
    return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private String b64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private byte[] signature(String data) {
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(key, "HmacSHA256"));
      return m.doFinal(data.getBytes(StandardCharsets.US_ASCII));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String accessToken(String user, String session) {
    try {
      long now = Instant.now().getEpochSecond();
      String data =
          b64(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")))
              + "."
              + b64(
                  json.writeValueAsBytes(
                      Map.of(
                          "sub",
                          user,
                          "sessionId",
                          session,
                          "type",
                          "access",
                          "iss",
                          "readquest-api",
                          "aud",
                          "readquest-web",
                          "iat",
                          now,
                          "exp",
                          now + 900,
                          "jti",
                          UUID.randomUUID().toString())));
      return data + "." + b64(signature(data));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String authenticate(String token) {
    try {
      if (token.length() > 4096) throw new IllegalArgumentException();
      String[] p = token.split("\\.");
      if (p.length != 3
          || !MessageDigest.isEqual(
              signature(p[0] + "." + p[1]), Base64.getUrlDecoder().decode(p[2])))
        throw new IllegalArgumentException();
      var h = json.readTree(Base64.getUrlDecoder().decode(p[0]));
      var c = json.readTree(Base64.getUrlDecoder().decode(p[1]));
      if (!"HS256".equals(h.path("alg").asText())
          || !"access".equals(c.path("type").asText())
          || !"readquest-api".equals(c.path("iss").asText())
          || !"readquest-web".equals(c.path("aud").asText())
          || !c.path("exp").isNumber()
          || c.path("exp").asLong() <= Instant.now().getEpochSecond()
          || (c.has("nbf") && c.path("nbf").asLong() > Instant.now().getEpochSecond()))
        throw new IllegalArgumentException();
      String id = c.path("sub").asText();
      User u = users.findById(id);
      RefreshSession s = sessions.findById(c.path("sessionId").asText());
      if (u == null
          || !"active".equals(u.getStatus())
          || s == null
          || !id.equals(s.getUserId())
          || sessions.activeFamily(id, s.getFamilyId() == null ? s.getId() : s.getFamilyId())
              == null
          || !s.getExpiresAt().isAfter(Instant.now())) throw new IllegalArgumentException();
      return id;
    } catch (Exception e) {
      throw new ApiException(401, "Invalid or expired access token");
    }
  }
}
