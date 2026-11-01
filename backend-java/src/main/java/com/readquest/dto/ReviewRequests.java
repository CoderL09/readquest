package com.readquest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ReviewRequests {
  private ReviewRequests() {}

  public record Start(@Min(5) @Max(20) int groupSize) {}

  public record Answer(
      @NotBlank @Pattern(regexp = "wordMeaning|conceptExplanation|transferSentence")
          String answerKind,
      @NotBlank @Pattern(regexp = "initial|guidedRevision") String stage,
      @NotBlank @Size(max = 4000) String answer,
      @Min(0) @Max(86400000) long responseTimeMs,
      @NotBlank @Size(max = 128) String idempotencyKey) {}

  public record Progression(@NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,36}") String eventId) {}

  public record Fragment(
      @NotBlank @Size(max = 2500) String text, @Min(0) Integer start, @Min(0) Integer end) {}

  public record Concept(
      @NotBlank @Size(max = 36) String bookId,
      @Size(max = 36) String chapterId,
      @NotBlank @Size(max = 512) String sourceSentenceKey,
      @NotBlank @Size(max = 5000) String originalSentence,
      @NotBlank @Size(max = 120) String category,
      @NotBlank @Size(max = 1000) String reverseQuestionZh,
      @NotBlank @Size(max = 4000) String referenceExplanationZh,
      @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 1000) String> requiredPointsZh,
      @Valid @Size(max = 20) List<Fragment> targetFragments) {}
}
