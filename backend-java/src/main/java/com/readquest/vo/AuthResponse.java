package com.readquest.vo;

import com.readquest.pojo.User;
import java.time.Instant;

public record AuthResponse(PublicUser user, String accessToken) {
  public record PublicUser(
      String id, String email, String displayName, String avatarUrl, Instant createdAt) {
    public static PublicUser of(User u) {
      return new PublicUser(
          u.getId(), u.getEmail(), u.getDisplayName(), u.getAvatarUrl(), u.getCreatedAt());
    }
  }
}
