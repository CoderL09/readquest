package com.readquest.pojo;

import java.time.Instant;

public record FsrsState(
    Instant dueAt,
    double stability,
    double difficulty,
    int elapsedDays,
    int scheduledDays,
    int learningSteps,
    int reps,
    int lapses,
    String state,
    Instant lastReviewedAt) {}
