package com.readquest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.service.TokenService;
import com.readquest.vo.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed only inside Spring Security, not as a second servlet filter. */
public final class BearerAuthenticationFilter extends OncePerRequestFilter {
  private final TokenService tokens;
  private final ObjectMapper json;

  public BearerAuthenticationFilter(TokenService tokens, ObjectMapper json) {
    this.tokens = tokens;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    if (path.isEmpty()) path = request.getRequestURI();
    return ("POST".equals(request.getMethod())
            && List.of("/api/users", "/api/sessions", "/api/sessions/current/tokens")
                .contains(path))
        || ("DELETE".equals(request.getMethod()) && "/api/sessions/current".equals(path));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if ("OPTIONS".equals(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    String header = request.getHeader("Authorization");
    if (header != null)
      try {
        if (!header.startsWith("Bearer ")) throw new ApiException(401, "Invalid authorization");
        String id = tokens.authenticate(header.substring(7));
        var authentication = new UsernamePasswordAuthenticationToken(id, null, List.of());
        authentication.setDetails(
            json.readTree(Base64.getUrlDecoder().decode(header.substring(7).split("\\.")[1]))
                .path("sessionId")
                .asText());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (ApiException error) {
        response.setStatus(401);
        response.setContentType("application/json");
        json.writeValue(
            response.getOutputStream(), ApiError.of("UNAUTHORIZED", error.getMessage()));
        return;
      }
    chain.doFilter(request, response);
  }
}
