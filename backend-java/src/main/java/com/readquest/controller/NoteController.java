package com.readquest.controller;

import com.readquest.dto.Requests.*;
import com.readquest.service.NoteService;
import com.readquest.vo.BookNoteView;
import com.readquest.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/book-notes")
public class NoteController {
  private final NoteService service;

  public NoteController(NoteService s) {
    service = s;
  }

  @GetMapping
  public PageResult<BookNoteView> list(
      Principal p,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return service.list(p.getName(), page, pageSize);
  }

  @GetMapping("/{id}")
  public BookNoteView get(Principal p, @PathVariable String id) {
    return service.get(p.getName(), id);
  }

  @PostMapping
  public ResponseEntity<BookNoteView> create(Principal p, @Valid @RequestBody CreateNote in) {
    BookNoteView n = service.create(p.getName(), in);
    return ResponseEntity.created(URI.create("/api/book-notes/" + n.getId())).body(n);
  }

  @PatchMapping("/{id}")
  public BookNoteView update(
      Principal p, @PathVariable String id, @Valid @RequestBody UpdateNote in) {
    return service.update(p.getName(), id, in);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(Principal p, @PathVariable String id) {
    service.delete(p.getName(), id);
    return ResponseEntity.noContent().build();
  }
}
