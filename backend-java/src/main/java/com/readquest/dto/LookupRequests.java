package com.readquest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class LookupRequests {
  private LookupRequests() {}

  public record Target(
      @NotBlank @Size(max = 2500) String text,
      @Size(max = 2500) String normalized,
      @Min(0) Integer start,
      @Min(0) Integer end) {}

  public record Dictionary(
      @NotBlank @Size(max = 120) String headword,
      @NotBlank @Size(max = 6000) String definition,
      @NotBlank @Size(max = 120) String sourceName) {}

  public record Create(
      @NotBlank @Size(max = 36) String bookId,
      @Size(max = 36) String chapterId,
      @Size(max = 128) String paragraphId,
      @Min(0) Integer paragraphIndex,
      @Size(max = 128) String sentenceId,
      @Size(max = 128) String chunkId,
      @Size(max = 512) String sourceSentenceKey,
      @NotBlank @Pattern(regexp = "word|phrase|sentence|grammar|sentencePattern") String type,
      @NotBlank @Size(max = 2500) String text,
      @Valid Target target,
      @Size(max = 5000) String originalSentence,
      @Size(max = 6000) String explanation,
      @Size(max = 6000) String translation,
      @Size(max = 6000) String grammarAnalysis,
      @Size(max = 6000) String readingStrategy,
      @Valid Dictionary dictionary) {}

  public record Enrichment(
      @Size(max = 6000) String explanation,
      @Size(max = 6000) String translation,
      @Size(max = 6000) String grammarAnalysis,
      @Size(max = 6000) String readingStrategy,
      @Valid Dictionary dictionary) {}
}
