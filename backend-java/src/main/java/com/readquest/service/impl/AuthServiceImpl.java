package com.readquest.service.impl;

import com.readquest.dto.Requests.*;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.vo.AuthResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  private final UserMapper users;
  private final SessionMapper sessions;
  private final TokenService tokens;
  private final PasswordEncoder passwords;
  private final ReadingMapper accountUsers;
  private final String dummy;

  public AuthServiceImpl(
      UserMapper u,
      SessionMapper s,
      TokenService t,
      PasswordEncoder p,
      ReadingMapper accountUsers) {
    users = u;
    sessions = s;
    tokens = t;
    passwords = p;
    this.accountUsers = accountUsers;
    dummy = p.encode("readquest-invalid-password-placeholder");
  }

  private String email(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public SessionResult register(Register in) {
    if (in.displayName().trim().isEmpty()) throw new ApiException(422, "Invalid display name");
    User u = new User();
    u.setId(UUID.randomUUID().toString());
    u.setEmail(email(in.email()));
    u.setDisplayName(in.displayName().trim());
    u.setAvatarUrl("");
    u.setPasswordHash(passwords.encode(in.password()));
    u.setCreatedAt(Instant.now());
    u.setUpdatedAt(u.getCreatedAt());
    users.insert(u);
    return issue(u);
  }

  @Transactional
  public SessionResult login(Login in) {
    User u = users.findByEmail(email(in.email()));
    boolean ok = passwords.matches(in.password(), u == null ? dummy : u.getPasswordHash());
    if (!ok || u == null || !"active".equals(u.getStatus()))
      throw new ApiException(401, "Invalid credentials");
    u = accountUsers.lockUser(u.getId());
    if (u == null) throw new ApiException(401, "Account unavailable");
    return issue(u);
  }

  private SessionResult issue(User u) {
    return issue(u, null);
  }

  private SessionResult issue(User u, String family) {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    RefreshSession s = new RefreshSession();
    s.setId(UUID.randomUUID().toString());
    s.setUserId(u.getId());
    s.setFamilyId(family == null ? s.getId() : family);
    s.setTokenHash(tokens.hash(token));
    s.setExpiresAt(Instant.now().plusSeconds(604800));
    sessions.insert(s);
    return new SessionResult(
        new AuthResponse(AuthResponse.PublicUser.of(u), tokens.accessToken(u.getId(), s.getId())),
        token);
  }

  private RefreshSession active(String token) {
    if (token == null || token.isBlank()) throw new ApiException(401, "Refresh token required");
    RefreshSession s = sessions.lockByHash(tokens.hash(token));
    if (s == null || s.getRevokedAt() != null || !s.getExpiresAt().isAfter(Instant.now()))
      throw new ApiException(401, "Invalid refresh session");
    return s;
  }

  @Transactional
  public SessionResult refresh(String token) {
    if (token == null || token.isBlank()) throw new ApiException(401, "Refresh token required");
    RefreshSession observed = sessions.findByHash(tokens.hash(token));
    if (observed == null) throw new ApiException(401, "Invalid refresh session");
    User u = accountUsers.lockUser(observed.getUserId());
    if (u == null) throw new ApiException(401, "Account unavailable");
    RefreshSession s = active(token);
    sessions.revoke(s.getId());
    return issue(u, s.getFamilyId());
  }

  @Transactional
  public void logout(String token) {
    if (token == null || token.isBlank()) return;
    RefreshSession observed = sessions.findByHash(tokens.hash(token));
    if (observed == null || accountUsers.lockUser(observed.getUserId()) == null) return;
    RefreshSession s = sessions.lockByHash(tokens.hash(token));
    if (s != null)
      sessions.revokeFamily(s.getUserId(), s.getFamilyId() == null ? s.getId() : s.getFamilyId());
  }

  public AuthResponse.PublicUser current(String id) {
    User u = users.findById(id);
    if (u == null || !"active".equals(u.getStatus()))
      throw new ApiException(401, "Account unavailable");
    return AuthResponse.PublicUser.of(u);
  }
}
