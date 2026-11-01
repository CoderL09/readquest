package com.readquest.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BookAssetTokens {
  private final byte[] key;
  private final ObjectMapper json;
  private final UserMapper users;
  private final SessionMapper sessions;

  public BookAssetTokens(
      @Value("${readquest.jwt-secret}") String secret,
      ObjectMapper json,
      UserMapper users,
      SessionMapper sessions)
      throws Exception {
    key =
        MessageDigest.getInstance("SHA-256")
            .digest(("java-book-assets:" + secret.strip()).getBytes(StandardCharsets.UTF_8));
    this.json = json;
    this.users = users;
    this.sessions = sessions;
  }

  private String base64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private byte[] sign(String value) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
  }

  public String url(String user, String book, String path) {
    try {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      Object details = auth == null ? null : auth.getDetails();
      String sid = details instanceof String text ? text : "";
      if (sid.isBlank()) return "";
      String payload =
          base64(
              json.writeValueAsBytes(
                  Map.of(
                      "v",
                      1,
                      "sub",
                      user,
                      "sid",
                      sid,
                      "book",
                      book,
                      "path",
                      path,
                      "exp",
                      Instant.now().plusSeconds(14400).getEpochSecond())));
      return "/api/books/"
          + book
          + "/assets/"
          + base64(path.getBytes(StandardCharsets.UTF_8))
          + "?token="
          + payload
          + "."
          + base64(sign(payload));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  public String verify(String book, String path, String token) {
    try {
      if (token == null || token.length() > 8192) throw new IllegalArgumentException();
      String[] pieces = token.split("\\.");
      if (pieces.length != 2
          || !MessageDigest.isEqual(sign(pieces[0]), Base64.getUrlDecoder().decode(pieces[1])))
        throw new IllegalArgumentException();
      var value = json.readTree(Base64.getUrlDecoder().decode(pieces[0]));
      String user = value.path("sub").asText();
      var account = users.findById(user);
      var session = sessions.findById(value.path("sid").asText());
      if (value.path("v").asInt() != 1
          || !book.equals(value.path("book").asText())
          || !path.equals(value.path("path").asText())
          || !value.path("exp").isIntegralNumber()
          || value.path("exp").asLong() <= Instant.now().getEpochSecond()
          || account == null
          || !"active".equals(account.getStatus())
          || session == null
          || !user.equals(session.getUserId())
          || session.getFamilyId() == null
          || session.getFamilyId().isBlank()
          || sessions.activeFamily(user, session.getFamilyId()) == null)
        throw new IllegalArgumentException();
      return user;
    } catch (Exception error) {
      throw new ApiException(404, "Book asset not found");
    }
  }
}
