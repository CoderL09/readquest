package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.dictionary.DictionaryProcesses;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.vo.AuthResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class AccountServiceImpl implements AccountService {
  private final AccountMapper mapper;
  private final AccountDataMapper data;
  private final DictionaryMapper dictionaries;
  private final DictionaryProcesses processes;
  private final TokenService tokens;
  private final PasswordEncoder passwords;
  private final MembershipService limits;
  private final AccountWriteGuard guard;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final StudyGroupService groups;
  private final EmbeddingMapper embeddings;
  private final SceneImageService scenes;
  private final ActivationService activations;
  private final Path avatarRoot, dictionaryRoot;

  public AccountServiceImpl(
      AccountMapper mapper,
      AccountDataMapper data,
      DictionaryMapper dictionaries,
      DictionaryProcesses processes,
      TokenService tokens,
      PasswordEncoder passwords,
      MembershipService limits,
      AccountWriteGuard guard,
      ObjectMapper json,
      StudyGroupService groups,
      EmbeddingMapper embeddings,
      SceneImageService scenes,
      ActivationService activations,
      PlatformTransactionManager manager,
      @Value("${readquest.avatar-root:./private_uploads/avatars}") String avatars,
      @Value("${readquest.dictionary-root:./private_uploads/dictionaries}")
          String dictionariesRoot) {
    this.mapper = mapper;
    this.data = data;
    this.dictionaries = dictionaries;
    this.processes = processes;
    this.tokens = tokens;
    this.passwords = passwords;
    this.limits = limits;
    this.guard = guard;
    this.json = json;
    this.groups = groups;
    this.embeddings = embeddings;
    this.scenes = scenes;
    this.activations = activations;
    tx = new TransactionTemplate(manager);
    avatarRoot = Path.of(avatars).toAbsolutePath().normalize();
    dictionaryRoot = Path.of(dictionariesRoot).toAbsolutePath().normalize();
  }

  public StreamingResponseBody export(String user, String token) {
    if (!limits.rateLimit("account-rights", user, 10, 3600))
      throw new ApiException(429, "Account export limit reached");
    if (!user.equals(tokens.authenticate(token)))
      throw new ApiException(401, "Authentication required");
    return out -> {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
      write(
          user,
          token,
          out,
          () -> {
            var profile = mapper.lockUser(user);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", "readquest-account-export");
            result.put("version", 4);
            result.put("generatedAt", Instant.now());
            result.put("profile", AuthResponse.PublicUser.of(profile));
            String prefix = json.writeValueAsString(result);
            return prefix.substring(0, prefix.length() - 1) + ",\"data\":{";
          });
      boolean firstSection = true;
      for (var section : AccountDataMapper.SECTIONS) {
        if (System.nanoTime() > deadline) throw new IOException("Account export timed out");
        String prefix = (firstSection ? "" : ",") + json.writeValueAsString(section.name()) + ":[";
        firstSection = false;
        write(user, token, out, () -> prefix);
        String cursor = "";
        boolean first = true;
        while (true) {
          if (System.nanoTime() > deadline) throw new IOException("Account export timed out");
          String after = cursor;
          var rows =
              guard.execute(
                  user,
                  () -> {
                    if (!user.equals(tokens.authenticate(token)))
                      throw new ApiException(401, "Authentication required");
                    return data.page(section.name(), user, after);
                  });
          if (rows.isEmpty()) break;
          cursor = Objects.toString(rows.getLast().get("export_cursor"), "");
          if (cursor.isEmpty() || cursor.equals(after))
            throw new IOException("Export cursor did not advance");
          for (var row : rows) {
            row.remove("export_cursor");
            var normalized = new LinkedHashMap<String, Object>();
            for (var entry : row.entrySet()) {
              String key = camel(entry.getKey());
              Object value = entry.getValue();
              if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toInstant();
              if (key.endsWith("Json") && value instanceof String text) {
                value = json.readTree(text);
                key = key.substring(0, key.length() - 4);
              }
              normalized.put(key, value);
            }
            row.clear();
            row.putAll(normalized);
          }
          String encoded = json.writeValueAsString(rows);
          String chunk = (first ? "" : ",") + encoded.substring(1, encoded.length() - 1);
          first = false;
          write(user, token, out, () -> chunk);
          if (rows.size() < 250) break;
        }
        write(user, token, out, () -> "]");
      }
      write(user, token, out, () -> "}}");
    };
  }

  private String camel(String value) {
    var result = new StringBuilder();
    boolean upper = false;
    for (char ch : value.toCharArray()) {
      if (ch == '_') upper = true;
      else {
        result.append(upper ? Character.toUpperCase(ch) : ch);
        upper = false;
      }
    }
    return result.toString();
  }

  @FunctionalInterface
  private interface Chunk {
    String get() throws IOException;
  }

  private void write(String user, String token, OutputStream out, Chunk chunk) throws IOException {
    try {
      guard.execute(
          user,
          () -> {
            if (!user.equals(tokens.authenticate(token)))
              throw new ApiException(401, "Authentication required");
            try {
              out.write(chunk.get().getBytes(StandardCharsets.UTF_8));
              out.flush();
              return null;
            } catch (IOException error) {
              throw new UncheckedIOException(error);
            }
          });
    } catch (UncheckedIOException error) {
      throw error.getCause();
    }
  }

  public Map<String, Object> requestDeletion(String user, String password) {
    if (!limits.rateLimit("account-rights", user, 10, 3600))
      throw new ApiException(429, "Account rights limit reached");
    var job =
        tx.execute(status -> {
          var account = mapper.lockUser(user);
          if (account == null
              || !"active".equals(account.getStatus())
              || !passwords.matches(password, account.getPasswordHash()))
            throw new ApiException(
                403, "PASSWORD_CONFIRMATION_FAILED", "Password confirmation failed");
          var dictionary = dictionaries.find(user);
          String avatar = account.getAvatarUrl();
          String avatarName =
              avatar != null && avatar.startsWith("/uploads/avatars/")
                  ? avatar.substring("/uploads/avatars/".length())
                  : null;
          var created =
              new AccountDeletionJob(
                  UUID.randomUUID().toString(),
                  user,
                  "pending",
                  avatarName,
                  dictionary == null ? null : dictionary.storageKey(),
                  null,
                  0,
                  Instant.now(),
                  null);
          mapper.insert(created);
          mapper.block(user);
          mapper.revoke(user);
          return created;
        });
    try {
      execute(Objects.requireNonNull(job));
    } catch (RuntimeException error) {
      mapper.failed(job.id());
    }
    return present(Objects.requireNonNull(mapper.job(user)));
  }

  private Map<String, Object> present(AccountDeletionJob job) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", job.id());
    result.put("status", job.status());
    result.put("completedAt", job.completedAt());
    result.put("queued", !"completed".equals(job.status()));
    return result;
  }

  public void processDeletions() {
    for (var job : mapper.pending())
      try {
        execute(job);
      } catch (RuntimeException error) {
        mapper.failed(job.id());
      }
  }

  private void remove(Path root, String name, String pattern) throws IOException {
    if (name == null || !name.matches(pattern)) return;
    Path file = root.resolve(name).normalize();
    if (!file.getParent().equals(root) || Files.isSymbolicLink(file))
      throw new IOException("Invalid managed account asset");
    if (root.equals(dictionaryRoot)) processes.invalidate(file);
    Files.deleteIfExists(file);
  }

  private void execute(AccountDeletionJob candidate) {
    tx.executeWithoutResult(status -> {
      // All writers lock the user first. Deletion uses the same lock order and can be retried
      // after a crash.
      var user = mapper.lockUser(candidate.userId());
      var job = mapper.lockJob(candidate.id());
      if (job == null || "completed".equals(job.status())) return;
      if (user == null || !"deletion_pending".equals(user.getStatus()))
        throw new IllegalStateException("Deletion account state changed");
      try {
        remove(avatarRoot, job.avatarName(), "[a-f0-9-]{36}\\.png");
        remove(dictionaryRoot, job.dictionaryKey(), "[a-f0-9-]{36}\\.mdx");
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }
      mapper.revoke(job.userId());
      groups.cleanupAccount(job.userId(), "deleted-" + job.id());
      embeddings.deleteOwned(job.userId());
      scenes.deleteOwned(job.userId());
      activations.cleanup(job.userId());
      for (String table :
          List.of(
              "agent_runs",
              "review_attempts",
              "review_sessions",
              "review_cards",
              "lookup_items",
              "book_notes",
              "library_entries",
              "reading_progress",
              "reading_events",
              "reading_profiles",
              "calibration_sessions",
              "feature_usage",
              "user_entitlements",
              "user_dictionaries",
              "ai_analysis_cache",
              "book_import_jobs",
              "remote_import_jobs")) data.delete(table, job.userId());
      mapper.closeBooks(job.userId());
      mapper.anonymize(
          job.userId(),
          job.id() + "@deleted.invalid",
          passwords.encode(UUID.randomUUID().toString()));
      mapper.completed(job.id());
    });
  }
}
