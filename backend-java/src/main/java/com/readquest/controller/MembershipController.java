package com.readquest.controller;

import com.readquest.pojo.*;
import com.readquest.service.MembershipService;
import com.readquest.vo.AuthResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class MembershipController {
  private final MembershipService service;

  public MembershipController(MembershipService s) {
    service = s;
  }

  public record Redeem(@NotBlank @Size(max = 64) String code) {}

  public record Profile(@NotBlank @Size(max = 50) String displayName) {}

  @GetMapping("/entitlements")
  public Map<String, Object> active(Principal user) {
    return Map.of("entitlements", service.active(user.getName()));
  }

  @PostMapping("/membership-redemptions")
  public ResponseEntity<?> redeem(Principal user, @Valid @RequestBody Redeem input) {
    if (!service.rateLimit("redeem", user.getName(), 12, 3600))
      throw new com.readquest.exception.ApiException(
          429, "RATE_LIMITED", "Too many redemption attempts");
    return ResponseEntity.status(201)
        .body(Map.of("success", true, "entitlement", service.redeem(user.getName(), input.code())));
  }

  @PatchMapping
  public Map<String, AuthResponse.PublicUser> profile(
      Principal user, @Valid @RequestBody Profile input) {
    return Map.of("user", service.updateName(user.getName(), input.displayName()));
  }

  @GetMapping("/sessions")
  public Map<String, Object> sessions(Principal user) {
    return Map.of("sessions", service.sessions(user.getName()));
  }

  @DeleteMapping("/sessions/{id}")
  public ResponseEntity<Void> revoke(Principal user, @PathVariable String id) {
    service.revokeSession(user.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
