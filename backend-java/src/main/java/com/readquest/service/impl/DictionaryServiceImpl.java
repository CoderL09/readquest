package com.readquest.service.impl;

import com.readquest.dictionary.DictionaryProcesses;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.UserDictionary;
import com.readquest.service.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DictionaryServiceImpl implements DictionaryService {
  private final DictionaryMapper mapper;
  private final ReadingMapper users;
  private final DictionaryProcesses workers;
  private final MembershipService limits;
  private final TransactionTemplate tx;
  private final Path root;

  public DictionaryServiceImpl(
      DictionaryMapper mapper,
      ReadingMapper users,
      DictionaryProcesses workers,
      MembershipService limits,
      PlatformTransactionManager manager,
      @Value("${readquest.dictionary-root:./private_uploads/dictionaries}") String root) {
    this.mapper = mapper;
    this.users = users;
    this.workers = workers;
    this.limits = limits;
    this.tx = new TransactionTemplate(manager);
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  private Path path(String key) {
    if (!key.matches("[a-f0-9-]{36}\\.mdx"))
      throw new IllegalStateException("Invalid dictionary storage key");
    Path result = root.resolve(key).normalize();
    if (!result.getParent().equals(root))
      throw new IllegalStateException("Invalid dictionary path");
    return result;
  }

  private Map<String, Object> view(UserDictionary d) {
    if (d == null) return null;
    return Map.of(
        "id",
        d.id(),
        "displayName",
        d.displayName(),
        "originalFileName",
        d.originalFileName(),
        "byteSize",
        d.byteSize(),
        "uploadedAt",
        d.createdAt(),
        "updatedAt",
        d.updatedAt());
  }

  public Map<String, Object> current(String user) {
    return view(mapper.find(user));
  }

  private void active(String user) {
    if (users.lockUser(user) == null) throw new ApiException(403, "Account is not active");
  }

  private void remove(String key) {
    Path file = path(key);
    workers.invalidate(file);
    try {
      Files.deleteIfExists(file);
    } catch (IOException error) {
      /* Orphan is private and may be retried by maintenance. */
    }
  }

  public Map<String, Object> install(String user, MultipartFile file) {
    if (!limits.rateLimit("dictionary-upload", user, 10, 3600))
      throw new ApiException(429, "Dictionary upload limit reached");
    String name = Objects.toString(file.getOriginalFilename(), "").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    if (name.length() > 255 || !name.toLowerCase(Locale.ROOT).endsWith(".mdx"))
      throw new ApiException(422, "Choose an MDX dictionary file");
    if (file.isEmpty() || file.getSize() > 80L * 1024 * 1024)
      throw new ApiException(413, "MDX dictionary must be nonempty and 80MB or smaller");
    tx.executeWithoutResult(status -> active(user));
    String key = UUID.randomUUID() + ".mdx";
    Path destination = path(key);
    boolean committed = false;
    try {
      Files.createDirectories(root);
      var hash = MessageDigest.getInstance("SHA-256");
      long bytes = 0;
      try (var in = file.getInputStream();
          var out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
        byte[] buffer = new byte[16384];
        int count;
        while ((count = in.read(buffer)) != -1) {
          bytes += count;
          if (bytes > 80L * 1024 * 1024) throw new ApiException(413, "MDX dictionary exceeds 80MB");
          hash.update(buffer, 0, count);
          out.write(buffer, 0, count);
        }
      }
      workers.validate(destination);
      Instant now = Instant.now();
      String display = name.substring(0, name.length() - 4).strip();
      if (display.isEmpty()) display = "My dictionary";
      var dictionary =
          new UserDictionary(
              UUID.randomUUID().toString(),
              user,
              display.substring(0, Math.min(120, display.length())),
              name,
              key,
              bytes,
              HexFormat.of().formatHex(hash.digest()),
              now,
              now);
      UserDictionary previous =
          tx.execute(status -> {
            active(user);
            var old = mapper.find(user);
            mapper.delete(user);
            mapper.insert(dictionary);
            return old;
          });
      committed = true;
      if (previous != null) remove(previous.storageKey());
      return view(dictionary);
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(500, "Dictionary installation failed");
    } finally {
      if (!committed) remove(key);
    }
  }

  public void delete(String user) {
    var old =
        tx.execute(status -> {
          active(user);
          var d = mapper.find(user);
          if (d == null) throw new ApiException(404, "Dictionary not found");
          mapper.delete(user);
          return d;
        });
    remove(Objects.requireNonNull(old).storageKey());
  }

  public Map<String, Object> lookup(String user, String word) {
    if (word == null || word.isBlank() || word.length() > 100)
      throw new ApiException(422, "A word of up to 100 characters is required");
    if (!limits.rateLimit("dictionary-lookup", user, 180, 60))
      throw new ApiException(429, "Dictionary lookup limit reached");
    var d = mapper.find(user);
    if (d == null) return null;
    var result = workers.lookup(path(d.storageKey()), candidates(word));
    if (!result.hasNonNull("definition")) return null;
    return Map.of(
        "headword",
        result.path("headword").asText(),
        "definition",
        result.path("definition").asText(),
        "sourceName",
        d.displayName());
  }

  public static List<String> candidates(String input) {
    String word =
        Normalizer.normalize(input, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    var result = new LinkedHashSet<String>();
    result.add(word);
    if (!word.matches("[a-z]+(?:['-][a-z]+)*")) return List.copyOf(result);
    if (word.endsWith("ies") && word.length() > 4)
      result.add(word.substring(0, word.length() - 3) + "y");
    else if (word.endsWith("es") && word.length() > 4) {
      result.add(word.substring(0, word.length() - 2));
      result.add(word.substring(0, word.length() - 1));
    } else if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3)
      result.add(word.substring(0, word.length() - 1));
    if (word.endsWith("ied") && word.length() > 4)
      result.add(word.substring(0, word.length() - 3) + "y");
    else if (word.endsWith("ed") && word.length() > 4) {
      String stem = word.substring(0, word.length() - 2);
      result.add(
          stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)
              ? stem.substring(0, stem.length() - 1)
              : stem.matches(".*[gvc]$") ? stem + "e" : stem);
    }
    if (word.endsWith("ing") && word.length() > 5) {
      String stem = word.substring(0, word.length() - 3);
      result.add(
          stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)
              ? stem.substring(0, stem.length() - 1)
              : stem);
      if (stem.matches(".*[kvgc]$")) result.add(stem + "e");
    }
    return List.copyOf(result);
  }
}
