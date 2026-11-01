package com.readquest.controller;

import com.readquest.pojo.BookImportJob;
import com.readquest.service.IngestionService;
import java.net.URI;
import java.security.Principal;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class IngestionController {
  private final IngestionService service;

  public IngestionController(IngestionService s) {
    service = s;
  }

  @PostMapping(value = "/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<IngestionService.Admission> create(
      Principal user,
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "false") boolean rightsAttested,
      @RequestParam(defaultValue = "quest") String mode,
      @RequestParam(defaultValue = "500") int wordsPerQuest) {
    var result = service.upload(user.getName(), file, rightsAttested, mode, wordsPerQuest);
    String location =
        result.job() == null
            ? "/api/books/" + result.book().getId()
            : "/api/book-imports/" + result.job().getId();
    return ResponseEntity.status(result.duplicate() ? 200 : 202)
        .location(URI.create(location))
        .body(result);
  }

  @PostMapping("/books/{id}/imports")
  public ResponseEntity<BookImportJob> retry(Principal user, @PathVariable String id) {
    var job = service.retry(user.getName(), id);
    return ResponseEntity.accepted()
        .location(URI.create("/api/book-imports/" + job.getId()))
        .body(job);
  }

  @GetMapping("/book-imports/{id}")
  public BookImportJob get(Principal user, @PathVariable String id) {
    return service.get(user.getName(), id);
  }

  @PostMapping("/book-imports/{id}/cancellations")
  public BookImportJob cancel(Principal user, @PathVariable String id) {
    return service.cancel(user.getName(), id);
  }

  @DeleteMapping("/books/{id}")
  public ResponseEntity<Void> remove(Principal user, @PathVariable String id) {
    service.remove(user.getName(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/books/{id}/source")
  public ResponseEntity<Resource> source(Principal user, @PathVariable String id) {
    Resource file = service.download(user.getName(), id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.getFilename()).build().toString())
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(file);
  }
}
