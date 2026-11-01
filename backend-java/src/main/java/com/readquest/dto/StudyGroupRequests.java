package com.readquest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public final class StudyGroupRequests {
  private StudyGroupRequests() {}

  public record Message(
      @NotBlank @Size(max = 1000) String body,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String clientMessageId) {}

  public record Reason(@NotBlank @Size(max = 500) String reason) {}

  public record Moderation(
      @Pattern(regexp = "member|moderator") String role,
      JsonNode mutedUntil,
      @NotBlank @Size(max = 500) String reason) {}

  public record Block(@NotBlank @Size(max = 36) String bookId) {}

  public record Report(
      @NotBlank @Pattern(regexp = "message|user") String targetType,
      @Size(max = 36) String targetMessageId,
      @Size(max = 36) String targetUserId,
      @NotBlank @Pattern(regexp = "harassment|spam|hate|sexual|violence|other") String reason,
      @Size(max = 1000) String details) {}

  public record Coach(
      @NotBlank @Pattern(regexp = "express|check|reply_suggestions") String action,
      @Size(max = 1000) String text,
      @NotBlank @Pattern(regexp = "A1|A2|B1|B2|C1|C2") String level) {}
}
