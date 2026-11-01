package com.readquest.pojo;

import java.time.Instant;

public record ChapterQuiz(
    String chapterId,
    String bookId,
    String sourceHash,
    String questionsJson,
    String status,
    String requestId,
    Instant leaseExpiresAt,
    Instant updatedAt) {}
