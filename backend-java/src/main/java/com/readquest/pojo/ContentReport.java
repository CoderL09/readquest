package com.readquest.pojo;

import java.time.Instant;

public record ContentReport(
    String id,
    String bookId,
    String reporterUserId,
    String targetType,
    String targetMessageId,
    String targetUserId,
    String reason,
    String details,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
