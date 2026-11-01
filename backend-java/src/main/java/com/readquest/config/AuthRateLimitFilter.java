package com.readquest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.MembershipService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-90)
@ConditionalOnProperty(
    name = "readquest.rate-limits.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private final MembershipService service;
  private final ObjectMapper json;

  public AuthRateLimitFilter(MembershipService s, ObjectMapper j) {
    service = s;
    json = j;
  }

  protected boolean shouldNotFilter(HttpServletRequest r) {
    return !"POST".equals(r.getMethod())
        || !List.of("/api/users", "/api/sessions", "/api/sessions/current/tokens")
            .contains(r.getRequestURI());
  }

  protected void doFilterInternal(
      HttpServletRequest r, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!service.rateLimit("auth", r.getRemoteAddr(), 20, 900)) {
      response.setStatus(429);
      response.setHeader("Retry-After", "900");
      response.setContentType("application/json");
      json.writeValue(
          response.getOutputStream(),
          com.readquest.vo.ApiError.of("RATE_LIMITED", "Too many authentication requests"));
      return;
    }
    chain.doFilter(r, response);
  }
}
