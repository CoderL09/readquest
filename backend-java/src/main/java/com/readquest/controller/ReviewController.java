package com.readquest.controller;

import com.readquest.dto.ReviewRequests.*;
import com.readquest.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {
  private final ReviewService service;

  public ReviewController(ReviewService s) {
    service = s;
  }

  @GetMapping("/users/me/review-summary")
  public Map<String, Object> summary(Principal user) {
    return service.summary(user.getName());
  }

  @GetMapping("/review-cards")
  public Map<String, Object> library(
      Principal user,
      @RequestParam(required = false) String kind,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "24") @Min(1) @Max(100) int pageSize) {
    return service.library(user.getName(), kind, page, pageSize);
  }

  @PostMapping("/review-cards")
  public ResponseEntity<?> concept(Principal user, @Valid @RequestBody Concept input) {
    var card = service.createConcept(user.getName(), input);
    return ResponseEntity.created(URI.create("/api/review-cards/" + card.get("id"))).body(card);
  }

  @GetMapping("/review-cards/{id}")
  public Map<String, Object> card(Principal user, @PathVariable String id) {
    return service.card(user.getName(), id);
  }

  @GetMapping("/review-cards/{id}/source")
  public Map<String, Object> source(Principal user, @PathVariable String id) {
    return service.source(user.getName(), id);
  }

  @GetMapping("/review-sessions/current")
  public Map<String, Object> active(Principal user) {
    return Collections.singletonMap("session", service.active(user.getName()));
  }

  @PostMapping("/review-sessions")
  public ResponseEntity<?> start(Principal user, @Valid @RequestBody Start input) {
    var session = service.start(user.getName(), input.groupSize());
    return session == null
        ? ResponseEntity.ok(Collections.singletonMap("session", null))
        : ResponseEntity.created(URI.create("/api/review-sessions/" + session.get("id")))
            .body(Map.of("session", session));
  }

  @GetMapping("/review-sessions/{id}")
  public Map<String, Object> session(Principal user, @PathVariable String id) {
    return service.get(user.getName(), id);
  }

  @PostMapping("/review-sessions/{id}/cards/{card}/answers")
  public Map<String, Object> answer(
      Principal user,
      @PathVariable String id,
      @PathVariable String card,
      @Valid @RequestBody Answer input) {
    return service.answer(user.getName(), id, card, input);
  }

  @PostMapping("/review-sessions/{id}/progressions")
  public Map<String, Object> advance(
      Principal user, @PathVariable String id, @Valid @RequestBody Progression input) {
    return Map.of("session", service.advance(user.getName(), id, input.eventId()));
  }

  @PostMapping("/review-sessions/{id}/completions")
  public Map<String, Object> complete(Principal user, @PathVariable String id) {
    return service.complete(user.getName(), id);
  }
}
