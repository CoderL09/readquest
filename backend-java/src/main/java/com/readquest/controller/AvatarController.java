package com.readquest.controller;

import com.readquest.service.AvatarService;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AvatarController {
  private final AvatarService service;

  public AvatarController(AvatarService service) {
    this.service = service;
  }

  @PutMapping(value = "/api/users/me/avatar", consumes = "multipart/form-data")
  public Map<String, Object> replace(Principal user, @RequestPart("avatar") MultipartFile file) {
    return Map.of("user", service.replace(user.getName(), file));
  }

  @GetMapping("/uploads/avatars/{name}")
  public ResponseEntity<Resource> image(@PathVariable String name) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(service.image(name));
  }
}
