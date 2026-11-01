package com.readquest.controller;

import com.readquest.service.BookAssetService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class BookAssetController {
  private final BookAssetService service;

  public BookAssetController(BookAssetService service) {
    this.service = service;
  }

  @GetMapping("/api/books/{book}/assets/{path}")
  public ResponseEntity<byte[]> asset(
      @PathVariable String book, @PathVariable String path, @RequestParam String token) {
    var value = service.read(book, path, token);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(value.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox; default-src 'none'")
        .body(value.bytes());
  }
}
