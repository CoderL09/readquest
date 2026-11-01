package com.readquest.pojo;

import java.time.Instant;

public record RemoteImportJob(
    String id,
    String userId,
    String requestUrl,
    String urlHash,
    String displayUrl,
    String title,
    String kind,
    String status,
    String bookId,
    String fileJobId,
    String runId,
    int attempts,
    Instant leaseExpiresAt,
    String errorCode,
    String activeOwner,
    Instant createdAt,
    Instant updatedAt,
    String sourceMetadataJson) {}
