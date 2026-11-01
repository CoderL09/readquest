package com.readquest.controller;

import com.readquest.dto.LookupRequests.*;
import com.readquest.service.LookupService;
import com.readquest.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LookupController {
  private final LookupService service;

  public LookupController(LookupService s) {
    service = s;
  }

  @PostMapping("/lookup-items")
  public ResponseEntity<?> create(Principal user, @Valid @RequestBody Create in) {
    var item = service.create(user.getName(), in);
    return ResponseEntity.created(URI.create("/api/lookup-items/" + item.get("id")))
        .body(Map.of("item", item));
  }

  @GetMapping("/lookup-items")
  public Map<String, Object> list(
      Principal user,
      @RequestParam(required = false) String bookId,
      @RequestParam(required = false) String chapterId,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "30") @Min(1) @Max(500) int limit) {
    return Map.of("items", service.list(user.getName(), bookId, chapterId, type, limit));
  }

  @GetMapping("/lookup-items/{id}")
  public Map<String, Object> get(Principal user, @PathVariable String id) {
    return Map.of("item", service.get(user.getName(), id));
  }

  @PatchMapping("/lookup-items/{id}")
  public Map<String, Object> update(
      Principal user, @PathVariable String id, @Valid @RequestBody Enrichment in) {
    return Map.of("item", service.enrich(user.getName(), id, in));
  }

  @DeleteMapping("/lookup-items/{id}")
  public ResponseEntity<Void> delete(Principal user, @PathVariable String id) {
    service.delete(user.getName(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/vocabulary")
  public PageResult<Map<String, Object>> vocabulary(
      Principal user,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int pageSize) {
    return service.vocabulary(user.getName(), page, pageSize);
  }

  @GetMapping("/users/me/lookup-summary")
  public Map<String, Object> summary(Principal user) {
    return service.summary(user.getName());
  }

  @GetMapping("/books/{book}/chapters/{chapter}/lookup-summary")
  public Map<String, Object> paragraphs(
      Principal user, @PathVariable String book, @PathVariable String chapter) {
    return Map.of("paragraphs", service.paragraphs(user.getName(), book, chapter));
  }
}
