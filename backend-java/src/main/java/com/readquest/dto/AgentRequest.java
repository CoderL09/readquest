package com.readquest.dto;

import jakarta.validation.constraints.*;

public record AgentRequest(
    @NotBlank @Size(max = 36) String bookId,
    @Size(max = 36) String chapterId,
    @NotBlank @Size(max = 1500) String goal,
    @Min(5) @Max(20) int groupSize) {}
