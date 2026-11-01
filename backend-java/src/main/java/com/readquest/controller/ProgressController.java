package com.readquest.controller;

import com.readquest.dto.Requests.Progress;
import com.readquest.pojo.ReadingProgress;
import com.readquest.service.ProgressService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books/{bookId}/chapters/{chapterId}/progress")
public class ProgressController {
  private final ProgressService service;

  public ProgressController(ProgressService s) {
    service = s;
  }

  @GetMapping
  public ReadingProgress get(
      Principal p, @PathVariable String bookId, @PathVariable String chapterId) {
    return service.get(p.getName(), bookId, chapterId);
  }

  @PutMapping
  public ReadingProgress put(
      Principal p,
      @PathVariable String bookId,
      @PathVariable String chapterId,
      @Valid @RequestBody Progress in) {
    return service.put(p.getName(), bookId, chapterId, in);
  }
}
