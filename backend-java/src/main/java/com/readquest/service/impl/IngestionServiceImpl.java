package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionServiceImpl implements IngestionService {
  private final IngestionMapper mapper;
  private final DocumentParser parser;
  private final QuestSegmentationService quests;
  private final TransactionTemplate tx;
  private final Path root;
  private final ScheduledExecutorService heartbeats =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "import-lease-renewal");
        thread.setDaemon(true);
        return thread;
      });

  @jakarta.annotation.PreDestroy
  public void close() {
    heartbeats.shutdownNow();
  }

  public IngestionServiceImpl(
      IngestionMapper mapper,
      DocumentParser parser,
      QuestSegmentationService quests,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${readquest.storage-root:./private_uploads/books}") String storage) {
    this.mapper = mapper;
    this.parser = parser;
    this.quests = quests;
    tx = new TransactionTemplate(manager);
    root = Path.of(storage).toAbsolutePath().normalize();
  }

  private void active(String user) {
    if (mapper.lockActiveUser(user) == null) throw new ApiException(403, "Account is not active");
  }

  private Path resolve(String filename) {
    if (filename == null) throw new ApiException(404, "Source file unavailable");
    Path p = root.resolve(filename).normalize();
    if (!p.startsWith(root) || !p.getParent().equals(root) || Files.isSymbolicLink(p))
      throw new ApiException(404, "Source file unavailable");
    return p;
  }

  public Admission upload(String user, MultipartFile file, boolean rights) {
    return upload(user, file, rights, "continuous", 500);
  }

  public Admission upload(
      String user, MultipartFile file, boolean rights, String mode, int wordsPerQuest) {
    if (!Set.of("quest", "continuous").contains(mode)
        || wordsPerQuest < 200
        || wordsPerQuest > 5000)
      throw new ApiException(422, "Invalid reading mode or quest length");
    if (!rights) throw new ApiException(422, "Rights attestation required");
    if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024)
      throw new ApiException(413, "Choose a file up to 50 MB");
    String name = Objects.requireNonNullElse(file.getOriginalFilename(), "");
    int dot = name.lastIndexOf('.');
    String extension = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    if (!List.of(".txt", ".pdf", ".epub", ".html").contains(extension))
      throw new ApiException(422, "Choose TXT, PDF, EPUB, or readable HTML");
    try {
      Files.createDirectories(root);
      byte[] bytes = file.getBytes();
      if (extension.equals(".pdf")
          && (bytes.length < 5
              || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                  .equals("%PDF-")))
        throw new ApiException(422, "PDF contents do not match its extension");
      if (extension.equals(".epub") && (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K'))
        throw new ApiException(422, "EPUB contents do not match its extension");
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      return tx.execute(status -> {
        active(user);
        Book duplicate = mapper.findDuplicate(user, hash);
        if (duplicate != null)
          return new Admission(duplicate, mapper.latestJob(user, duplicate.getId()), true);
        String id = UUID.randomUUID().toString();
        String filename = id + extension;
        Path destination = resolve(filename);
        try {
          Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
          throw new ApiException(500, "Could not store source file");
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              public void afterCompletion(int state) {
                if (state != STATUS_COMMITTED)
                  try {
                    Files.deleteIfExists(destination);
                  } catch (IOException e) {
                    org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("Could not remove rolled-back source file");
                  }
              }
            });
        Book book = new Book();
        book.setId(id);
        book.setOwnerId(user);
        String title = name.substring(0, dot).replaceAll("[\\p{Cntrl}]", "").strip();
        book.setTitle(
            title.isBlank() ? "Untitled" : title.substring(0, Math.min(title.length(), 1000)));
        book.setAuthor("Unknown Author");
        book.setFilePath(filename);
        book.setFileHash(hash);
        book.setReadingMode(extension.equals(".epub") ? "continuous" : mode);
        book.setStatus("uploaded");
        book.setProgress(0);
        book.setRightsAttestedAt(Instant.now());
        book.setCreatedAt(Instant.now());
        book.setUpdatedAt(book.getCreatedAt());
        mapper.insertBook(book);
        BookImportJob job = enqueue(user, id, book.getReadingMode(), wordsPerQuest);
        return new Admission(book, job, false);
      });
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(500, "Could not accept source file");
    }
  }

  private BookImportJob enqueue(String user, String book) {
    Book source = mapper.lockBook(book);
    return enqueue(user, book, source.getReadingMode(), 500);
  }

  private BookImportJob enqueue(String user, String book, String mode, int wordsPerQuest) {
    BookImportJob job = new BookImportJob();
    job.setId(UUID.randomUUID().toString());
    job.setUserId(user);
    job.setBookId(book);
    job.setReadingMode(mode);
    job.setWordsPerQuest(wordsPerQuest);
    job.setStatus("queued");
    job.setCreatedAt(Instant.now());
    job.setUpdatedAt(job.getCreatedAt());
    mapper.insertJob(job);
    mapper.resetBook(book);
    return job;
  }

  public BookImportJob get(String user, String id) {
    BookImportJob job = mapper.findJob(user, id);
    if (job == null) throw new ApiException(404, "Import job not found");
    return job;
  }

  public BookImportJob retry(String user, String id) {
    return tx.execute(status -> {
      active(user);
      Book b = mapper.lockBook(id);
      if (b == null || !user.equals(b.getOwnerId()) || "removed".equals(b.getVisibility()))
        throw new ApiException(404, "Book not found");
      BookImportJob latest = mapper.latestJob(user, id);
      if (latest != null && !Set.of("failed", "cancelled").contains(latest.getStatus()))
        return latest;
      if (b.getFilePath() == null) throw new ApiException(409, "No source file available");
      return enqueue(
          user, id, b.getReadingMode(), latest == null ? 500 : latest.getWordsPerQuest());
    });
  }

  public BookImportJob cancel(String user, String id) {
    return tx.execute(status -> {
      active(user);
      BookImportJob job = get(user, id);
      Book book = mapper.lockBook(job.getBookId());
      if (book == null || !user.equals(book.getOwnerId()) || "removed".equals(book.getVisibility()))
        throw new ApiException(404, "Book not found");
      job = get(user, id);
      if (Set.of("queued", "processing").contains(job.getStatus())) {
        mapper.cancelJobs(book.getId());
        mapper.cancelBook(book.getId());
      }
      return get(user, id);
    });
  }

  private void checkpoint(BookImportJob job, String run, String phase, int progress) {
    tx.executeWithoutResult(status -> {
      Book book = mapper.lockBook(job.getBookId());
      if (book == null
          || "removed".equals(book.getVisibility())
          || mapper.checkpoint(job.getId(), run, phase, progress) != 1)
        throw new ApiException(409, "Import lease is no longer active");
      if (mapper.progressBook(book.getId(), progress) != 1)
        throw new ApiException(409, "Import is no longer active");
    });
  }

  public void processPending() {
    for (BookImportJob exhausted : mapper.exhausted())
      tx.executeWithoutResult(s -> {
        if (mapper.failJob(exhausted.getId(), exhausted.getRunId(), "Import retry limit reached")
            > 0) mapper.failBook(exhausted.getBookId(), "Import retry limit reached");
      });
    for (BookImportJob candidate : mapper.pending()) {
      String run = UUID.randomUUID().toString();
      if (mapper.claim(candidate.getId(), run) == 0) continue;
      AtomicBoolean lost = new AtomicBoolean();
      ScheduledFuture<?> heartbeat =
          heartbeats.scheduleAtFixedRate(
              () -> {
                try {
                  if (mapper.renew(candidate.getId(), run) != 1) lost.set(true);
                } catch (Exception error) {
                  lost.set(true);
                }
              },
              20,
              20,
              TimeUnit.SECONDS);
      try {
        checkpoint(candidate, run, "parsing", 10);
        Book book = tx.execute(s -> mapper.lockBook(candidate.getBookId()));
        if (book == null) throw new ApiException(404, "Book not found");
        var parsed = parser.parseBook(resolve(book.getFilePath()));
        if (lost.get()) throw new ApiException(409, "Import lease is no longer active");
        checkpoint(candidate, run, "segmenting", 45);
        BookImportJob claimed = mapper.findJob(candidate.getUserId(), candidate.getId());
        var stages =
            quests.prepare(
                candidate.getUserId(),
                book.getId(),
                parsed.sections(),
                claimed.getReadingMode(),
                claimed.getWordsPerQuest());
        List<DocumentParser.Section> sections =
            stages.stream().map(QuestSegmentationService.Stage::section).toList();
        if (lost.get()) throw new ApiException(409, "Import lease is no longer active");
        checkpoint(candidate, run, "persisting", 80);
        tx.executeWithoutResult(s -> {
          active(candidate.getUserId());
          Book locked = mapper.lockBook(candidate.getBookId());
          BookImportJob job = mapper.lockClaim(candidate.getId(), run);
          if (job == null) throw new ApiException(409, "Import lease expired");
          if (locked == null || "removed".equals(locked.getVisibility()))
            throw new ApiException(404, "Book removed");
          long words = 0;
          int index = 0;
          for (var section : sections) {
            Chapter c = new Chapter();
            c.setId(UUID.randomUUID().toString());
            c.setBookId(book.getId());
            c.setChapterIndex(index++);
            c.setStageTitle(section.title());
            c.setStageSummary(stages.get(index - 1).summary());
            c.setContent(section.text());
            c.setContentText(section.text());
            c.setContentHtml(section.html());
            c.setSourceHref(section.sourceHref());
            mapper.insertChapter(c);
            words += section.text().split("\\s+").length;
          }
          mapper.completeBook(book.getId(), sections.size(), words);
          try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (locked.getMetadataJson() != null)
              metadata.putAll(
                  new com.fasterxml.jackson.databind.ObjectMapper()
                      .readValue(
                          locked.getMetadataJson(),
                          new com.fasterxml.jackson.core.type.TypeReference<
                              Map<String, Object>>() {}));
            metadata.putAll(parsed.metadata());
            String parsedTitle =
                metadata.get("title") instanceof List<?> values && !values.isEmpty()
                    ? values.getFirst().toString()
                    : "";
            if (metadata.get("preferredTitle") instanceof String preferred && !preferred.isBlank())
              parsedTitle = preferred;
            String author =
                metadata.get("creator") instanceof List<?> values
                    ? String.join(", ", values.stream().map(Object::toString).toList())
                    : "";
            String cover =
                metadata.get("coverPath") instanceof String path
                    ? "rqasset:" + path
                    : Objects.toString(locked.getCoverUrl(), "");
            if (cover.isBlank()
                && metadata.get("catalogCoverUrl") instanceof String external
                && external.length() <= 2000) cover = external;
            mapper.metadata(
                book.getId(),
                parsedTitle.substring(0, Math.min(1000, parsedTitle.length())),
                author.substring(0, Math.min(500, author.length())),
                cover,
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata));
          } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
          }
          mapper.finishJob(job.getId(), run);
        });
      } catch (Exception e) {
        String message =
            e instanceof ApiException ? e.getMessage() : "Import could not be completed";
        tx.executeWithoutResult(s -> {
          if (mapper.failJob(candidate.getId(), run, message) > 0)
            mapper.failBook(candidate.getBookId(), message);
        });
      } finally {
        heartbeat.cancel(false);
      }
    }
  }

  public void remove(String user, String id) {
    tx.executeWithoutResult(s -> {
      active(user);
      if (mapper.removeBook(id, user) == 0) throw new ApiException(404, "Book not found");
      mapper.cancelJobs(id);
    });
  }

  public Resource download(String user, String id) {
    Book b = tx.execute(s -> mapper.lockBook(id));
    if (b == null || !user.equals(b.getOwnerId()) || "removed".equals(b.getVisibility()))
      throw new ApiException(404, "Source file not found");
    Path file = resolve(b.getFilePath());
    if (!Files.isRegularFile(file)) throw new ApiException(404, "Source file not found");
    return new FileSystemResource(file);
  }
}
