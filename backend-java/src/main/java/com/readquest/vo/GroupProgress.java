package com.readquest.vo;

import java.time.Instant;

public record GroupProgress(
    String userId,
    long completedParagraphs,
    long totalParagraphs,
    long completedChapters,
    Instant lastActiveAt) {}
