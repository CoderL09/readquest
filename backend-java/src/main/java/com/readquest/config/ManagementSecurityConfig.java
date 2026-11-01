package com.readquest.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Metrics are not granted to ordinary application users. */
@Configuration
public class ManagementSecurityConfig {
  @Bean
  @Order(0)
  SecurityFilterChain managementSecurity(
      HttpSecurity http,
      @Value("${readquest.management-token:}") String token,
      com.fasterxml.jackson.databind.ObjectMapper json)
      throws Exception {
    return http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(rules ->
            rules
                .requestMatchers(EndpointRequest.to("health"))
                .permitAll()
                .anyRequest()
                .access((authentication, context) -> {
                  String header = context.getRequest().getHeader("Authorization");
                  return new AuthorizationDecision(
                      token.length() >= 32
                          && header != null
                          && header.startsWith("Bearer ")
                          && MessageDigest.isEqual(
                              token.getBytes(StandardCharsets.UTF_8),
                              header.substring(7).getBytes(StandardCharsets.UTF_8)));
                }))
        .exceptionHandling(errors ->
            errors.authenticationEntryPoint((request, response, error) -> {
              response.setStatus(401);
              response.setContentType("application/json");
              json.writeValue(
                  response.getOutputStream(),
                  com.readquest.vo.ApiError.of("UNAUTHORIZED", "Management token required"));
            }))
        .build();
  }
}
