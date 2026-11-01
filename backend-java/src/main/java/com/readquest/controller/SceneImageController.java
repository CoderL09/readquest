package com.readquest.controller;

import com.readquest.service.SceneImageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books/{book}/scene-images")
public class SceneImageController {
  private final SceneImageService service;

  public SceneImageController(SceneImageService service) {
    this.service = service;
  }

  public record Generate(@NotBlank @Size(max = 1800) String prompt) {}

  @PostMapping
  public ResponseEntity<?> generate(
      Principal user, @PathVariable String book, @Valid @RequestBody Generate input) {
    String url = service.generate(user.getName(), book, input.prompt());
    return ResponseEntity.created(java.net.URI.create(url)).body(Map.of("imageUrl", url));
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> read(
      @PathVariable String book,
      @PathVariable String id,
      @RequestParam(required = false) String token) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.read(book, id, token));
  }
}
