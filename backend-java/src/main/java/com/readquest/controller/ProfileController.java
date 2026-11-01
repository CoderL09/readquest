package com.readquest.controller;

import com.readquest.dto.CalibrationResponseInput;
import com.readquest.service.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProfileController {
  private final ProfileService service;

  public ProfileController(ProfileService s) {
    service = s;
  }

  @GetMapping("/users/me/growth")
  public Map<String, Object> growth(Principal user) {
    return service.growth(user.getName());
  }

  @GetMapping("/users/me/recommendations")
  public Map<String, Object> library(Principal user) {
    return service.library(user.getName());
  }

  @GetMapping("/users/me/reading-passport")
  public Map<String, Object> passport(Principal user) {
    return service.passport(user.getName());
  }

  @GetMapping("/users/me/reading-profile")
  public Map<String, Object> profile(Principal user) {
    return Map.of("profile", service.passport(user.getName()).get("profile"));
  }

  @PostMapping("/catalog/books/{id}/imports")
  public void retiredImport(@PathVariable String id) {
    throw new com.readquest.exception.ApiException(
        410, "LEGACY_BOOK_SOURCE_RETIRED", "Use a verified catalog offer instead");
  }

  @GetMapping("/catalog/books/{id}")
  public Map<String, Object> metadata(Principal user, @PathVariable String id) {
    return service.metadata(user.getName(), id);
  }

  @PostMapping("/calibration-sessions")
  public ResponseEntity<?> start(Principal user) {
    var session = service.startCalibration(user.getName());
    return ResponseEntity.created(
            URI.create("/api/calibration-sessions/" + session.get("sessionId")))
        .body(session);
  }

  @GetMapping("/calibration-sessions/{id}")
  public Map<String, Object> calibration(Principal user, @PathVariable String id) {
    return service.calibration(user.getName(), id);
  }

  @PostMapping("/calibration-sessions/{id}/samples/{step}/responses")
  public Map<String, Object> response(
      Principal user,
      @PathVariable String id,
      @PathVariable @Min(1) @Max(3) int step,
      @Valid @RequestBody CalibrationResponseInput input) {
    return service.respond(user.getName(), id, step, input);
  }
}
