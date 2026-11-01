package com.readquest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ConversationRequest(
    @NotBlank @Size(max = 2500) String sentence,
    @NotBlank @Size(max = 800) String question,
    @Size(max = 36) String chapterId,
    @Size(max = 36) String sentenceId,
    @Size(max = 180) String sourceSentenceKey,
    @Size(max = 16) List<@NotNull @Valid Turn> history) {
  public record Turn(
      @NotBlank @Pattern(regexp = "user|assistant") String role,
      @NotBlank @Size(max = 1600) String content) {}
}
