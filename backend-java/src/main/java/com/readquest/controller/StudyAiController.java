package com.readquest.controller;

import com.readquest.service.StudyAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class StudyAiController {
  private final StudyAiService service;

  public StudyAiController(StudyAiService service) {
    this.service = service;
  }

  public record Intervention(
      @NotBlank @Size(max = 10000) String surroundingText,
      @NotBlank @Size(max = 5000) String stuckChunk) {}

  public record ContextEmotion(@NotBlank @Size(max = 5000) String currentSentence) {}

  @PostMapping("/books/{book}/context-analyses")
  public com.fasterxml.jackson.databind.JsonNode contextEmotion(
      Principal user, @PathVariable String book, @Valid @RequestBody ContextEmotion in) {
    return service.contextEmotion(user.getName(), book, in.currentSentence());
  }

  @PostMapping("/reading-interventions")
  public Map<String, Object> intervention(Principal user, @Valid @RequestBody Intervention in) {
    return Map.of(
        "intervention_result",
        service.intervention(user.getName(), in.surroundingText(), in.stuckChunk()));
  }

  @GetMapping("/chapters/{chapter}/quiz")
  public Map<String, Object> quiz(Principal user, @PathVariable String chapter) {
    return service.quiz(user.getName(), chapter, false);
  }

  @PostMapping("/chapters/{chapter}/quizzes")
  public Map<String, Object> generate(Principal user, @PathVariable String chapter) {
    return service.quiz(user.getName(), chapter, true);
  }
}
