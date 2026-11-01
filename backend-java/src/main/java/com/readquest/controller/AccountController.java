package com.readquest.controller;

import com.readquest.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/users/me")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  public record Deletion(
      @NotBlank @Pattern(regexp = "DELETE") String confirmation,
      @NotBlank @Size(min = 8, max = 128) String password) {}

  @GetMapping("/data-export")
  public ResponseEntity<StreamingResponseBody> export(
      Principal user, @RequestHeader("Authorization") String authorization) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=readquest-account-export.json")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(service.export(user.getName(), authorization.substring(7)));
  }

  @PostMapping("/deletion-requests")
  public ResponseEntity<?> delete(Principal user, @Valid @RequestBody Deletion in) {
    return ResponseEntity.accepted()
        .body(Map.of("deletion", service.requestDeletion(user.getName(), in.password())));
  }
}
