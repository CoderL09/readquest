package com.readquest.controller;

import com.readquest.dto.Requests.Shelf;
import com.readquest.pojo.LibraryEntry;
import com.readquest.service.LibraryService;
import com.readquest.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/library/books")
public class LibraryController {
  private final LibraryService service;

  public LibraryController(LibraryService s) {
    service = s;
  }

  @GetMapping
  public PageResult<com.readquest.vo.LibraryEntryView> list(
      Principal p,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return service.list(p.getName(), page, pageSize);
  }

  @PutMapping("/{bookId}")
  public LibraryEntry put(Principal p, @PathVariable String bookId, @Valid @RequestBody Shelf in) {
    return service.put(p.getName(), bookId, in.shelfState());
  }

  @DeleteMapping("/{bookId}")
  public ResponseEntity<Void> delete(Principal p, @PathVariable String bookId) {
    service.delete(p.getName(), bookId);
    return ResponseEntity.noContent().build();
  }
}
