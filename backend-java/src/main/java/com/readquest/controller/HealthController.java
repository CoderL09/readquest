package com.readquest.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private final org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator database;

  public HealthController(javax.sql.DataSource dataSource) {
    database = new org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator(dataSource);
  }

  @GetMapping("/api/health")
  public org.springframework.http.ResponseEntity<Map<String, String>> health() {
    boolean up =
        org.springframework.boot.actuate.health.Status.UP.equals(database.health().getStatus());
    return org.springframework.http.ResponseEntity.status(up ? 200 : 503)
        .body(
            Map.of(
                "status",
                up ? "ok" : "unavailable",
                "service",
                "readquest-java",
                "database",
                "mysql"));
  }
}
