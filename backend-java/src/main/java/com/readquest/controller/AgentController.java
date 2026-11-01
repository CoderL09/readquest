package com.readquest.controller;

import com.readquest.dto.AgentRequest;
import com.readquest.service.AgentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentController {
  private final AgentService service;

  public AgentController(AgentService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<?> create(
      Principal user,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AgentRequest input) {
    var run = service.create(user.getName(), key, input);
    return ResponseEntity.created(URI.create("/api/agent-runs/" + run.get("id"))).body(run);
  }

  @GetMapping
  public Map<String, Object> list(Principal user) {
    return Map.of("items", service.list(user.getName()));
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(Principal user, @PathVariable String id) {
    return service.get(user.getName(), id);
  }

  @PutMapping("/{id}/confirmation")
  public Map<String, Object> confirm(Principal user, @PathVariable String id) {
    return service.confirm(user.getName(), id);
  }
}
