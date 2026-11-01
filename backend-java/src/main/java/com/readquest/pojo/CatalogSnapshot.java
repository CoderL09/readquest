package com.readquest.pojo;

import java.time.Instant;

public record CatalogSnapshot(
    String cacheKey, String itemsJson, Instant fetchedAt, Instant expiresAt) {}
