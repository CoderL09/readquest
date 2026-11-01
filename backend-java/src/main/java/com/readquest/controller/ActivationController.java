package com.readquest.controller;

import com.readquest.service.ActivationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ActivationController {
  private final ActivationService service;

  public ActivationController(ActivationService service) {
    this.service = service;
  }

  public record Create(@NotBlank @Email @Size(max = 254) String email) {}

  @GetMapping("/membership-offers")
  public Map<String, Object> settings() {
    return service.settings();
  }

  @PostMapping("/activation-purchases")
  public ResponseEntity<?> create(
      Principal user, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create in) {
    var value = service.create(user.getName(), in.email(), key);
    return ResponseEntity.created(URI.create("/api/activation-purchases/" + value.get("id")))
        .body(value);
  }

  @GetMapping("/activation-purchases")
  public Map<String, Object> list(Principal user) {
    return Map.of("purchases", service.list(user.getName()));
  }

  @GetMapping("/activation-purchases/{id}")
  public Map<String, Object> get(Principal user, @PathVariable String id) {
    return service.get(user.getName(), id);
  }

  @PutMapping("/activation-purchases/{id}/simulated-payment")
  public Map<String, Object> confirm(Principal user, @PathVariable String id) {
    return service.confirm(user.getName(), id);
  }

  @PostMapping("/activation-purchases/{id}/email-deliveries")
  public ResponseEntity<?> retry(Principal user, @PathVariable String id) {
    return ResponseEntity.accepted().body(service.retry(user.getName(), id));
  }
}
