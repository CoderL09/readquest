package com.readquest.dto;

import jakarta.validation.constraints.*;

public record CalibrationResponseInput(
    @Min(0) @Max(900) double readingSeconds,
    @Min(0) @Max(50) double lookupCount,
    @DecimalMin("0") @DecimalMax("1") double comprehensionScore,
    @DecimalMin("0") @DecimalMax("1") double flowScore) {}
