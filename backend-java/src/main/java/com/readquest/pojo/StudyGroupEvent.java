package com.readquest.pojo;

import java.time.Instant;

public record StudyGroupEvent(
    long sequence, String bookId, String messageId, String eventType, Instant createdAt) {}
