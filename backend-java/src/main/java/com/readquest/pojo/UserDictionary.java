package com.readquest.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record UserDictionary(
    String id,
    String userId,
    String displayName,
    String originalFileName,
    @JsonIgnore String storageKey,
    long byteSize,
    @JsonIgnore String checksumSha256,
    Instant createdAt,
    Instant updatedAt) {}
