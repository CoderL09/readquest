package com.readquest.controller;

import com.readquest.dto.ReadingEventInput;
import com.readquest.pojo.ReadingProgress;
import com.readquest.service.ReadingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReadingController {
  private final ReadingService service;

  public ReadingController(ReadingService s) {
    service = s;
  }

  @GetMapping("/chapters/{id}/structure")
  public Map<String, Object> structure(Principal user, @PathVariable String id) {
    return service.structure(user.getName(), id);
  }

  @GetMapping("/chapters/{id}/sentences")
  public Map<String, Object> sentences(Principal user, @PathVariable String id) {
    return service.sentences(user.getName(), id);
  }

  @PostMapping("/books/{bookId}/chapters/{chapterId}/reading-events")
  public ReadingProgress event(
      Principal user,
      @PathVariable String bookId,
      @PathVariable String chapterId,
      @Valid @RequestBody ReadingEventInput input) {
    return service.record(user.getName(), bookId, chapterId, input);
  }
}
