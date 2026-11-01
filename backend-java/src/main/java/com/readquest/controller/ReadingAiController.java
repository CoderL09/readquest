package com.readquest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.readquest.service.ReadingAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ReadingAiController {
  private final ReadingAiService service;

  public ReadingAiController(ReadingAiService s) {
    service = s;
  }

  public record Article(@NotBlank @Size(max = 30000) String text) {}

  public record Chunks(
      @NotBlank @Size(max = 20000) String text,
      @NotNull @Pattern(regexp = "original|phrase") String mode) {}

  public record SentenceChunks(@NotBlank @Size(max = 160) String sentenceId) {}

  @PostMapping("/books/{book}/chapters/{chapter}/sentence-chunks")
  public Map<String, Object> sentenceChunks(
      Principal user,
      @PathVariable String book,
      @PathVariable String chapter,
      @Valid @RequestBody SentenceChunks in) {
    return service.sentenceChunks(user.getName(), book, chapter, in.sentenceId());
  }

  public record Explanation(
      @NotBlank @Size(max = 2500) String text,
      @NotBlank @Pattern(regexp = "word|meaning|hint|grammar|translation") String action,
      @Size(max = 2500) String contextSentence,
      List<Map<String, String>> existingGroups) {}

  public record Chat(
      @NotBlank @Size(max = 2500) String question, @Size(max = 36) String chapterId) {}

  @PostMapping(value = "/article-analyses", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> article(Principal user, @Valid @RequestBody Article in) {
    return ResponseEntity.ok()
        .header("X-Accel-Buffering", "no")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store, no-transform")
        .body(service.article(user.getName(), in.text()));
  }

  @PostMapping("/reading-chunks")
  public Map<String, Object> chunks(Principal user, @Valid @RequestBody Chunks in) {
    return service.chunks(user.getName(), in.text(), in.mode());
  }

  @PostMapping("/books/{id}/reading-explanations")
  public Map<String, JsonNode> explain(
      Principal user, @PathVariable String id, @Valid @RequestBody Explanation in) {
    return Map.of(
        "analysis",
        service.explain(
            user.getName(),
            id,
            in.text(),
            in.action(),
            Objects.requireNonNullElse(in.contextSentence(), "")));
  }

  @PostMapping("/books/{id}/chat-messages")
  public Map<String, Object> chat(
      Principal user, @PathVariable String id, @Valid @RequestBody Chat in) {
    return service.chat(user.getName(), id, in.question(), in.chapterId());
  }
}
