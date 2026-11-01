package com.readquest.controller;

import com.readquest.service.DictionaryService;
import java.security.Principal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me/dictionary")
public class DictionaryController {
  private final DictionaryService service;

  public DictionaryController(DictionaryService service) {
    this.service = service;
  }

  private Map<String, Object> envelope(String key, Object value) {
    var result = new LinkedHashMap<String, Object>();
    result.put(key, value);
    return result;
  }

  @GetMapping
  public Map<String, Object> current(Principal user) {
    return envelope("dictionary", service.current(user.getName()));
  }

  @PutMapping(consumes = "multipart/form-data")
  public ResponseEntity<?> install(Principal user, @RequestPart("file") MultipartFile file) {
    return ResponseEntity.ok(envelope("dictionary", service.install(user.getName(), file)));
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(Principal user) {
    service.delete(user.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/entries")
  public Map<String, Object> lookup(Principal user, @RequestParam String word) {
    return envelope("definition", service.lookup(user.getName(), word));
  }
}
