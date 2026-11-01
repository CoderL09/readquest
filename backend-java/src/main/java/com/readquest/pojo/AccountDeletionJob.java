package com.readquest.pojo;

import java.time.Instant;

public record AccountDeletionJob(
    String id,
    String userId,
    String status,
    String avatarName,
    String dictionaryKey,
    String lastError,
    int attempts,
    Instant requestedAt,
    Instant completedAt) {}
