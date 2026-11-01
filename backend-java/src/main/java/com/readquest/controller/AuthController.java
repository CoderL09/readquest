package com.readquest.controller;

import com.readquest.dto.Requests.*;
import com.readquest.service.AuthService;
import com.readquest.vo.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService service;
  private final boolean secure;

  public AuthController(AuthService s, @Value("${readquest.secure-cookie}") boolean secure) {
    service = s;
    this.secure = secure;
  }

  private ResponseEntity<AuthResponse> response(AuthService.SessionResult r, int status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.SET_COOKIE, cookie(r.refreshToken(), 604800))
        .body(r.response());
  }

  private String cookie(String value, long age) {
    return ResponseCookie.from("rq_refresh", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/api/sessions")
        .maxAge(age)
        .build()
        .toString();
  }

  @PostMapping("/users")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody Register in) {
    return response(service.register(in), 201);
  }

  @PostMapping("/sessions")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody Login in) {
    return response(service.login(in), 201);
  }

  @PostMapping("/sessions/current/tokens")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = "rq_refresh", required = false) String token) {
    return response(service.refresh(token), 200);
  }

  @DeleteMapping("/sessions/current")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "rq_refresh", required = false) String token) {
    service.logout(token);
    return ResponseEntity.noContent()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.SET_COOKIE, cookie("", 0))
        .build();
  }

  @GetMapping("/users/me")
  public ResponseEntity<AuthResponse.PublicUser> me(java.security.Principal p) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.current(p.getName()));
  }
}
