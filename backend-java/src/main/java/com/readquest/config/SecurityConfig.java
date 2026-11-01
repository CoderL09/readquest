package com.readquest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.TokenService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${readquest.allowed-origins}") String origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.asList(origins.split(",")));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"));
    c.setExposedHeaders(List.of("X-Request-ID", "Retry-After", "Location"));
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      TokenService tokens,
      ObjectMapper json,
      @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled)
      throws Exception {
    var filter = new BearerAuthenticationFilter(tokens, json);
    return http.cors(c -> {})
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a ->
            a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                .permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(
                        docsEnabled))
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",
                    "/site.webmanifest",
                    "/app",
                    "/app/**",
                    "/login",
                    "/register",
                    "/calibration",
                    "/passport",
                    "/review",
                    "/library",
                    "/discover",
                    "/sources",
                    "/vocabulary",
                    "/notes",
                    "/groups",
                    "/groups/**",
                    "/profile",
                    "/billing",
                    "/path",
                    "/progress",
                    "/journey",
                    "/upload",
                    "/read/**",
                    "/reader/**")
                .permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/users",
                    "/api/sessions",
                    "/api/sessions/current/tokens")
                .permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.DELETE, "/api/sessions/current")
                .permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/uploads/avatars/*")
                .permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/books/*/assets/*",
                    "/api/books/*/scene-images/*")
                .permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/sources",
                    "/api/catalog/categories",
                    "/api/catalog/search",
                    "/api/catalog/trending")
                .permitAll()
                .requestMatchers("/api/health")
                .permitAll()
                .anyRequest()
                .authenticated())
        .exceptionHandling(e ->
            e.authenticationEntryPoint((req, res, ex) -> {
              res.setStatus(401);
              res.setContentType("application/json");
              json.writeValue(
                  res.getOutputStream(),
                  com.readquest.vo.ApiError.of("UNAUTHORIZED", "Authentication required"));
            }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
