package com.readquest.pojo;

import java.time.Instant;

public record SceneImage(
    String id,
    String userId,
    String bookId,
    String fileName,
    Instant retentionUntil,
    Instant createdAt) {}
