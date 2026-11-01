package com.readquest.controller;

import com.readquest.service.RemoteImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RemoteImportController {
  private final RemoteImportService service;

  public RemoteImportController(RemoteImportService service) {
    this.service = service;
  }

  public record Inspection(@NotBlank @Size(max = 2048) String url) {}

  public record Import(
      @NotBlank @Size(max = 2048) String url,
      @Size(max = 300) String title,
      @AssertTrue boolean rightsAttested) {}

  @PostMapping("/source-inspections")
  public Map<String, Object> inspect(Principal user, @Valid @RequestBody Inspection in) {
    return Map.of("inspection", service.inspect(user.getName(), in.url()));
  }

  @PostMapping("/remote-imports")
  public ResponseEntity<?> create(Principal user, @Valid @RequestBody Import in) {
    return ResponseEntity.accepted()
        .body(service.create(user.getName(), in.url(), in.title(), "web-article"));
  }

  @GetMapping("/remote-imports/{id}")
  public Map<String, Object> get(Principal user, @PathVariable String id) {
    return Map.of("job", service.get(user.getName(), id));
  }
}
