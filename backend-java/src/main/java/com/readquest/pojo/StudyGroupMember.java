package com.readquest.pojo;

import java.time.Instant;

public record StudyGroupMember(
    String id,
    String bookId,
    String userId,
    String role,
    Instant mutedUntil,
    Instant createdAt,
    Instant updatedAt) {}
