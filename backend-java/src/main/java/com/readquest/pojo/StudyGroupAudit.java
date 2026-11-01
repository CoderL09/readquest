package com.readquest.pojo;

import java.time.Instant;

public record StudyGroupAudit(
    String id,
    String bookId,
    String actorUserId,
    String action,
    String targetType,
    String targetId,
    String reason,
    Instant createdAt) {}
