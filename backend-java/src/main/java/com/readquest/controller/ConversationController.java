package com.readquest.controller;

import com.readquest.dto.ConversationRequest;
import com.readquest.service.ConversationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/books/{book}")
public class ConversationController {
  private final ConversationService service;

  public ConversationController(ConversationService service) {
    this.service = service;
  }

  @PostMapping(value = "/reading-conversations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> stream(
      Principal user,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String auth,
      @PathVariable String book,
      @Valid @RequestBody ConversationRequest input) {
    return ResponseEntity.ok()
        .header("X-Accel-Buffering", "no")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store, no-transform")
        .body(service.stream(user.getName(), auth.substring(7), book, input));
  }
}
