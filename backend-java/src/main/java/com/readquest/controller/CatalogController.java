package com.readquest.controller;

import com.readquest.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
  private final CatalogService service;

  public CatalogController(CatalogService service) {
    this.service = service;
  }

  @GetMapping("/sources")
  public Object sources() {
    return Map.of("sources", service.sources());
  }

  @GetMapping("/catalog/categories")
  public Object categories() {
    return Map.of("categories", service.categories());
  }

  @GetMapping("/catalog/search")
  public Object search(
      Principal user,
      HttpServletRequest request,
      @RequestParam String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "combined") String provider,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return service.search(
        user == null ? request.getRemoteAddr() : user.getName(),
        q,
        category,
        provider,
        cursor,
        limit);
  }

  @GetMapping("/catalog/trending")
  public Object trending(
      Principal user,
      HttpServletRequest request,
      @RequestParam(defaultValue = "week") String window,
      @RequestParam(defaultValue = "combined") String provider,
      @RequestParam(required = false) String category) {
    return service.trending(
        user == null ? request.getRemoteAddr() : user.getName(), window, provider, category);
  }

  public record Offer(
      @NotBlank @Size(max = 64) String providerId,
      @NotBlank @Size(max = 256) String externalId,
      @NotBlank @Size(max = 512) String offerId) {}

  @PostMapping("/catalog-imports")
  public ResponseEntity<?> importOffer(Principal user, @Valid @RequestBody Offer in) {
    return ResponseEntity.accepted()
        .body(service.importOffer(user.getName(), in.providerId(), in.externalId(), in.offerId()));
  }
}
