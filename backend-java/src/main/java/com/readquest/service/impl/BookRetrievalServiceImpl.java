package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.mapper.EmbeddingMapper;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BookRetrievalServiceImpl implements BookRetrievalService {
  private final BookService books;
  private final EmbeddingProvider provider;
  private final EmbeddingMapper cache;
  private final AccountWriteGuard guard;
  private final ObjectMapper json;

  public BookRetrievalServiceImpl(
      BookService books,
      EmbeddingProvider provider,
      EmbeddingMapper cache,
      AccountWriteGuard guard,
      ObjectMapper json) {
    this.books = books;
    this.provider = provider;
    this.cache = cache;
    this.guard = guard;
    this.json = json;
  }

  public boolean configured() {
    return provider.configured();
  }

  private String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((provider.modelKey() + "\n" + text).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  public Result retrieve(
      String user, String book, String query, String preferredChapter, boolean pastOnly) {
    books.requireReadable(user, book);
    if (preferredChapter != null) books.chapter(user, book, preferredChapter);
    List<Passage> passages = new ArrayList<>();
    boolean found = false;
    int sequence = 0;
    Pattern anchor =
        Pattern.compile(
            Arrays.stream(query.strip().split("\\s+"))
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("\\s+")),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    for (Chapter chapter : books.chapters(user, book)) {
      String text = Objects.requireNonNullElse(chapter.getContentText(), chapter.getContent());
      if (text == null) continue;
      String chapterHash = sourceHash(text);
      if (pastOnly) {
        var match = anchor.matcher(text);
        if (match.find()) {
          text = text.substring(0, match.start());
          found = true;
        }
      }
      for (int start = 0; start < text.length(); ) {
        int chunkStart = start;
        int end = Math.min(text.length(), start + 1600);
        if (end < text.length()) {
          int boundary = text.lastIndexOf(' ', end);
          if (boundary > start + 800) end = boundary;
          if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        }
        String raw = text.substring(start, end);
        String part = raw.strip();
        int passageStart = start + raw.length() - raw.stripLeading().length();
        start = end;
        if (part.isBlank()) continue;
        passages.add(
            new Passage(
                chapter.getId(),
                chapter.getStageTitle(),
                part,
                sequence++,
                0,
                passageStart,
                passageStart + part.length(),
                chapterHash));
        if (passages.size() > 10000)
          throw new ApiException(422, "Book exceeds retrieval size limit");
        if (end < text.length()) {
          int overlap = Math.max(chunkStart + 1, end - 200);
          while (overlap < end && !Character.isWhitespace(text.charAt(overlap - 1))) overlap++;
          if (overlap < end) start = overlap;
        }
      }
      if (found) break;
    }
    if (pastOnly && !found)
      throw new ApiException(
          422, "SENTENCE_NOT_IN_BOOK", "Current sentence must occur in the readable book");
    List<Passage> candidates =
        PassageRanking.rank(passages, query, preferredChapter, pastOnly).stream()
            .limit(32)
            .toList();
    if (!provider.configured() || candidates.isEmpty())
      return new Result(
          candidates.stream().limit(3).sorted(Comparator.comparingInt(Passage::index)).toList(),
          "lexical");
    double[] queryVector = provider.embed(List.of(query)).getFirst();
    Map<String, double[]> vectors = new HashMap<>();
    List<Passage> missing = new ArrayList<>();
    for (Passage passage : candidates) {
      String key = hash(passage.text()), stored = cache.find(book, key);
      try {
        double[] vector = stored == null ? null : json.readValue(stored, double[].class);
        if (vector == null || vector.length != queryVector.length) missing.add(passage);
        else vectors.put(key, vector);
      } catch (Exception invalidCache) {
        missing.add(passage);
      }
    }
    for (int offset = 0; offset < missing.size(); offset += 16) {
      List<Passage> batch = missing.subList(offset, Math.min(missing.size(), offset + 16));
      List<double[]> embedded = provider.embed(batch.stream().map(Passage::text).toList());
      guard.execute(
          user,
          () -> {
            books.requireReadable(user, book);
            for (int i = 0; i < batch.size(); i++) {
              String key = hash(batch.get(i).text());
              if (embedded.get(i).length != queryVector.length)
                throw new ApiException(502, "Embedding dimensions changed");
              try {
                cache.save(new BookEmbedding(book, key, json.writeValueAsString(embedded.get(i))));
              } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new IllegalStateException(error);
              }
              vectors.put(key, embedded.get(i));
            }
            return null;
          });
    }
    books.requireReadable(user, book);
    return new Result(
        candidates.stream()
            .map(p ->
                new Passage(
                    p.chapterId(),
                    p.title(),
                    p.text(),
                    p.index(),
                    cosine(queryVector, vectors.get(hash(p.text()))),
                    p.startOffset(),
                    p.endOffset(),
                    p.chapterHash()))
            .sorted(Comparator.comparingDouble(Passage::score).reversed())
            .limit(3)
            .sorted(Comparator.comparingInt(Passage::index))
            .toList(),
        "hybrid-vector");
  }

  public static double cosine(double[] left, double[] right) {
    if (left.length != right.length || left.length == 0)
      throw new ApiException(502, "Embedding dimensions do not match");
    double dot = 0, a = 0, b = 0;
    for (int i = 0; i < left.length; i++) {
      dot += left[i] * right[i];
      a += left[i] * left[i];
      b += right[i] * right[i];
    }
    if (!Double.isFinite(dot) || !Double.isFinite(a) || !Double.isFinite(b) || a <= 0 || b <= 0)
      throw new ApiException(502, "Invalid embedding vector");
    return dot / Math.sqrt(a) / Math.sqrt(b);
  }

  public static String sourceHash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
