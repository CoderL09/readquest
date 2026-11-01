package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.imports.*;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class RemoteImportServiceImpl implements RemoteImportService {
  private final RemoteFetcher remote;
  private final RemoteImportMapper mapper;
  private final IngestionService imports;
  private final IngestionMapper files;
  private final BookMapper books;
  private final AccountWriteGuard guard;
  private final MembershipService limits;
  private final ObjectMapper json;

  public RemoteImportServiceImpl(
      RemoteFetcher remote,
      RemoteImportMapper mapper,
      IngestionService imports,
      IngestionMapper files,
      BookMapper books,
      AccountWriteGuard guard,
      MembershipService limits,
      ObjectMapper json) {
    this.remote = remote;
    this.mapper = mapper;
    this.imports = imports;
    this.files = files;
    this.books = books;
    this.guard = guard;
    this.limits = limits;
    this.json = json;
  }

  private void limit(String user) {
    if (!limits.rateLimit("remote-import", user, 20, 3600)
        || !limits.rateLimit("remote-import-global", "all", 200, 3600))
      throw new ApiException(429, "RATE_LIMITED", "Remote import limit reached");
  }

  public Map<String, Object> inspect(String user, String url) {
    limit(user);
    guard.execute(user, () -> null);
    var resource = remote.fetch(PublicUrls.normalize(url).toString());
    String format = WebDocuments.format(resource);
    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("siteName", resource.url().getHost());
    preview.put("sizeBytes", resource.bytes().length);
    if (format.equals("html")) {
      var doc =
          WebDocuments.extract(WebDocuments.decode(resource.bytes()), resource.url().toString());
      preview.put("title", doc.title());
      preview.put("byline", doc.byline());
      preview.put("excerpt", doc.excerpt());
      preview.put("wordCount", doc.wordCount());
    } else {
      String name = resource.fileName().isBlank() ? "Imported book." + format : resource.fileName();
      preview.put("title", name.replaceFirst("\\.[^.]+$", "").replaceAll("[_-]+", " "));
      preview.put("fileName", name);
    }
    return Map.of(
        "id",
        UUID.randomUUID().toString(),
        "kind",
        format.equals("html") ? "web-article" : "direct-file-url",
        "displayUrl",
        PublicUrls.display(resource.url()),
        "originHost",
        resource.url().getHost(),
        "detectedFormat",
        format,
        "preview",
        preview,
        "expiresAt",
        Instant.now().plusSeconds(600));
  }

  private String hash(String input) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  public Map<String, Object> create(String user, String url, String title, String kind) {
    return create(user, url, title, kind, Map.of());
  }

  private String serialize(Map<String, Object> value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public Map<String, Object> create(
      String user, String url, String title, String kind, Map<String, Object> sourceMetadata) {
    limit(user);
    var uri = PublicUrls.normalize(url);
    String fingerprint = hash(uri.toString());
    try {
      return guard.execute(
          user,
          () -> {
            var previous = mapper.byUrl(user, fingerprint);
            if (previous != null) {
              boolean retry = "failed".equals(previous.status());
              if (retry) mapper.retry(user, previous.id());
              return Map.of("job", view(mapper.find(user, previous.id())), "deduplicated", !retry);
            }
            Instant now = Instant.now();
            var job =
                new RemoteImportJob(
                    UUID.randomUUID().toString(),
                    user,
                    uri.toString(),
                    fingerprint,
                    PublicUrls.display(uri),
                    Objects.toString(title, ""),
                    kind,
                    "queued",
                    null,
                    null,
                    null,
                    0,
                    null,
                    null,
                    user,
                    now,
                    now,
                    serialize(sourceMetadata));
            mapper.insert(job);
            return Map.of("job", view(job), "deduplicated", false);
          });
    } catch (DuplicateKeyException error) {
      throw new ApiException(409, "IMPORT_ALREADY_EXISTS", "Another remote import is active");
    }
  }

  public Map<String, Object> get(String user, String id) {
    var job = mapper.find(user, id);
    if (job == null) throw new ApiException(404, "Import job not found");
    reconcile(job);
    return view(mapper.find(user, id));
  }

  private Map<String, Object> view(RemoteImportJob job) {
    int percent =
        switch (job.status()) {
          case "fetching" -> 20;
          case "extracting" -> 50;
          case "ingesting" -> 75;
          case "completed" -> 100;
          default -> 0;
        };
    String phase =
        switch (job.status()) {
          case "fetching" -> "Downloading content";
          case "ingesting" -> "Preparing your reader";
          case "completed" -> "Ready to read";
          case "failed" -> "Import failed";
          default -> "Waiting to import";
        };
    String first = null;
    if (job.bookId() != null && job.status().equals("completed")) {
      var chapters = books.chapters(job.bookId());
      if (!chapters.isEmpty()) first = chapters.getFirst().getId();
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", job.id());
    value.put("kind", job.kind());
    value.put("status", job.status());
    value.put("progress", Map.of("phase", phase, "percent", percent));
    value.put(
        "error",
        job.errorCode() == null
            ? null
            : Map.of(
                "code",
                job.errorCode(),
                "message",
                "",
                "retryable",
                job.errorCode().equals("SOURCE_TEMPORARILY_UNAVAILABLE")));
    value.put("bookId", job.bookId());
    value.put("firstStageId", first);
    value.put("createdAt", job.createdAt());
    value.put("updatedAt", job.updatedAt());
    return value;
  }

  private void reconcile(RemoteImportJob job) {
    if (!job.status().equals("ingesting")) return;
    var book = job.bookId() == null ? null : books.findById(job.bookId());
    if (book == null || "removed".equals(book.getVisibility()) || "failed".equals(book.getStatus()))
      mapper.terminal(job.id(), "failed", "SOURCE_TEMPORARILY_UNAVAILABLE");
    else if ("completed".equals(book.getStatus())) mapper.terminal(job.id(), "completed", null);
  }

  public void processPending() {
    for (var candidate : mapper.pending()) {
      if (candidate.status().equals("ingesting")) {
        reconcile(candidate);
        continue;
      }
      String run = UUID.randomUUID().toString();
      if (candidate.attempts() >= 3) {
        mapper.terminal(candidate.id(), "failed", "SOURCE_TEMPORARILY_UNAVAILABLE");
        continue;
      }
      if (mapper.claim(candidate.id(), run) != 1) continue;
      try {
        Map<String, Object> sourceMetadata =
            candidate.sourceMetadataJson() == null
                ? Map.of()
                : json.readValue(
                    candidate.sourceMetadataJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        var resource = remote.fetch(candidate.requestUrl());
        String format = WebDocuments.format(resource);
        if (sourceMetadata.get("requestedFormat") instanceof String expected
            && !expected.equals(format))
          throw new ApiException(
              422,
              "DOWNLOAD_NOT_AUTHORIZED",
              "Downloaded format does not match verified catalog offer");
        if (format.equals("html"))
          WebDocuments.extract(WebDocuments.decode(resource.bytes()), resource.url().toString());
        String filename = resource.fileName();
        if (!filename.toLowerCase(Locale.ROOT).endsWith("." + format))
          filename = (filename.isBlank() ? "Imported book" : filename) + "." + format;
        var admitted =
            imports.upload(
                candidate.userId(),
                new MemoryUpload(filename, resource.contentType(), resource.bytes()),
                true);
        guard.execute(
            candidate.userId(),
            () -> {
              var book = files.lockBook(admitted.book().getId());
              try {
                Map<String, Object> metadata = new LinkedHashMap<>();
                if (book.getMetadataJson() != null)
                  metadata.putAll(
                      json.readValue(
                          book.getMetadataJson(),
                          new com.fasterxml.jackson.core.type.TypeReference<
                              Map<String, Object>>() {}));
                metadata.putAll(sourceMetadata);
                metadata.put("sourceUrl", PublicUrls.display(resource.url()));
                metadata.put(
                    "sourceKind", format.equals("html") ? "web-article" : "direct-file-url");
                if (!candidate.title().isBlank()) metadata.put("preferredTitle", candidate.title());
                mapper.source(book.getId(), candidate.title(), json.writeValueAsString(metadata));
              } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
              }
              if (mapper.ingest(
                      candidate.id(),
                      run,
                      book.getId(),
                      admitted.job() == null ? null : admitted.job().getId(),
                      candidate.kind().equals("catalog-offer")
                          ? "catalog-offer"
                          : format.equals("html") ? "web-article" : "direct-file-url")
                  != 1) throw new ApiException(409, "Import lease expired");
              return null;
            });
        reconcile(mapper.find(candidate.userId(), candidate.id()));
      } catch (Exception error) {
        String code =
            error instanceof ApiException api ? api.code() : "SOURCE_TEMPORARILY_UNAVAILABLE";
        mapper.fail(candidate.id(), run, code);
      }
    }
  }
}
