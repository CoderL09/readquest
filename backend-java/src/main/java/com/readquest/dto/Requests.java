package com.readquest.dto;

import jakarta.validation.constraints.*;

public final class Requests {
  private Requests() {}

  public record Register(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 50) String displayName,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  public record CreateNote(
      @NotBlank @Size(max = 36) String bookId,
      @Size(max = 36) String chapterId,
      @Min(0) Integer paragraphIndex,
      @NotBlank @Size(max = 5000) String quote,
      @Size(max = 4000) String note) {}

  public record UpdateNote(@NotNull @Size(max = 4000) String note) {}

  public record Shelf(@NotNull @Pattern(regexp = "saved|reading|completed") String shelfState) {}

  public record Progress(
      @Min(0) int currentParagraphIndex,
      @Min(0) int completedParagraphs,
      @Min(0) int totalParagraphs,
      @Min(0) long totalReadingMs) {}
}
