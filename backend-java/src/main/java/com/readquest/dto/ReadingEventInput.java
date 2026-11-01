package com.readquest.dto;

import jakarta.validation.constraints.*;

public record ReadingEventInput(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,36}") String eventId,
    @Min(0) int currentParagraphIndex,
    @Min(0) int completedParagraphs,
    @Min(0) int totalParagraphs,
    @Min(0) @Max(86400000) long readingDurationMs) {}
