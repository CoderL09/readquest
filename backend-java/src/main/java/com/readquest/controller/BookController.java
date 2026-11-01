package com.readquest.controller;

import com.readquest.pojo.*;
import com.readquest.service.BookService;
import com.readquest.vo.PageResult;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class BookController {
  private final BookService service;

  public BookController(BookService s) {
    service = s;
  }

  @GetMapping
  public PageResult<Book> list(
      Principal p,
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @RequestParam(defaultValue = "catalog") @Pattern(regexp = "catalog|uploads") String view) {
    return "uploads".equals(view)
        ? service.uploads(p.getName(), page, pageSize)
        : service.list(p.getName(), page, pageSize);
  }

  @GetMapping("/{id}")
  public Book get(Principal p, @PathVariable String id) {
    return service.get(p.getName(), id);
  }

  @GetMapping("/{bookId}/chapters")
  public List<Chapter> chapters(Principal p, @PathVariable String bookId) {
    return service.chapters(p.getName(), bookId);
  }

  @GetMapping("/{bookId}/chapters/{id}")
  public Chapter chapter(Principal p, @PathVariable String bookId, @PathVariable String id) {
    return service.chapter(p.getName(), bookId, id);
  }
}
