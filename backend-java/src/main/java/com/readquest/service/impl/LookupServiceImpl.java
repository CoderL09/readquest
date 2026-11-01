package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.dto.LookupRequests.*;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import com.readquest.vo.PageResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LookupServiceImpl implements LookupService {
  private final LookupMapper mapper;
  private final ReadingMapper users;
  private final BookService books;
  private final ObjectMapper json;
  private final ReviewService reviews;

  public LookupServiceImpl(
      LookupMapper m, ReadingMapper u, BookService b, ObjectMapper j, ReviewService r) {
    mapper = m;
    users = u;
    books = b;
    json = j;
    reviews = r;
  }

  private String text(String v) {
    return v == null ? "" : v.strip();
  }

  private String normalized(String v) {
    return Normalizer.normalize(v, Normalizer.Form.NFKC)
        .strip()
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private String hash(String v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String serialize(Object value) {
    try {
      return value == null ? null : json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Object deserialize(String value) {
    try {
      return value == null ? null : json.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("Stored lookup JSON is invalid");
    }
  }

  private void active(String user) {
    if (users.lockUser(user) == null) throw new ApiException(403, "Account is not active");
  }

  private Map<String, Object> view(LookupItem value) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", value.getId());
    result.put("bookId", value.getBookId());
    result.put("chapterId", value.getChapterId());
    result.put("paragraphId", value.getParagraphId());
    result.put("paragraphIndex", value.getParagraphIndex());
    result.put("sentenceId", value.getSentenceId());
    result.put("chunkId", value.getChunkId());
    result.put("sourceSentenceKey", value.getSourceSentenceKey());
    result.put("type", value.getType());
    result.put("text", value.getText());
    result.put("target", deserialize(value.getTargetJson()));
    result.put("originalSentence", value.getOriginalSentence());
    result.put("explanation", value.getExplanation());
    result.put("translation", value.getTranslation());
    result.put("grammarAnalysis", value.getGrammarAnalysis());
    result.put("readingStrategy", value.getReadingStrategy());
    result.put("dictionary", deserialize(value.getDictionaryJson()));
    result.put("reviewStatus", value.getReviewStatus());
    result.put("masteryLevel", value.getMasteryLevel());
    result.put("nextReviewAt", value.getNextReviewAt());
    result.put("lookupTime", value.getLookupTime());
    return result;
  }

  public Map<String, Object> get(String user, String id) {
    LookupItem value = mapper.find(user, id);
    if (value == null) throw new ApiException(404, "Lookup item not found");
    return view(value);
  }

  @Transactional
  public Map<String, Object> create(String user, Create in) {
    active(user);
    books.requireReadable(user, in.bookId());
    if (in.chapterId() != null) books.chapter(user, in.bookId(), in.chapterId());
    if (in.target() != null && (in.target().start() != null || in.target().end() != null)) {
      if (in.target().start() == null
          || in.target().end() == null
          || in.target().end() <= in.target().start()
          || in.target().end() > text(in.originalSentence()).length())
        throw new ApiException(422, "Invalid lookup target span");
      String actual = text(in.originalSentence()).substring(in.target().start(), in.target().end());
      if (!actual.equals(in.target().text()))
        throw new ApiException(422, "Lookup target does not match source sentence");
    }
    String source = text(in.sourceSentenceKey());
    if (source.isBlank())
      source =
          hash(
              in.bookId()
                  + "\u0000"
                  + in.chapterId()
                  + "\u0000"
                  + in.paragraphIndex()
                  + "\u0000"
                  + text(in.originalSentence()));
    String norm = normalized(in.target() == null ? in.text() : in.target().text());
    String occurrence =
        in.type().equals("word")
            ? hash(
                serialize(
                    Arrays.asList(
                        in.bookId(),
                        in.chapterId(),
                        in.paragraphIndex(),
                        source,
                        norm,
                        in.target() == null ? null : in.target().start(),
                        in.target() == null ? null : in.target().end())))
            : null;
    if (occurrence != null) {
      LookupItem existing = mapper.occurrence(user, occurrence);
      if (existing != null) {
        reviews.syncWord(existing);
        return view(existing);
      }
    }
    LookupItem value = new LookupItem();
    value.setId(UUID.randomUUID().toString());
    value.setUserId(user);
    value.setBookId(in.bookId());
    value.setChapterId(in.chapterId());
    value.setParagraphId(in.paragraphId());
    value.setParagraphIndex(in.paragraphIndex());
    value.setSentenceId(in.sentenceId());
    value.setChunkId(in.chunkId());
    value.setSourceSentenceKey(source);
    value.setOccurrenceKey(occurrence);
    value.setType(in.type());
    value.setText(in.text().strip());
    value.setNormalizedText(norm);
    value.setTargetJson(
        serialize(
            in.target() == null
                ? null
                : new Target(in.target().text(), norm, in.target().start(), in.target().end())));
    value.setOriginalSentence(text(in.originalSentence()));
    value.setExplanation(text(in.explanation()));
    value.setTranslation(text(in.translation()));
    value.setGrammarAnalysis(text(in.grammarAnalysis()));
    value.setReadingStrategy(text(in.readingStrategy()));
    value.setDictionaryJson(serialize(in.dictionary()));
    value.setReviewStatus("new");
    value.setMasteryLevel(0);
    value.setLookupTime(Instant.now());
    value.setNextReviewAt(value.getLookupTime().plusSeconds(600));
    value.setCreatedAt(value.getLookupTime());
    value.setUpdatedAt(value.getLookupTime());
    mapper.insert(value);
    reviews.syncWord(value);
    return view(value);
  }

  @Transactional
  public Map<String, Object> enrich(String user, String id, Enrichment in) {
    active(user);
    LookupItem value = mapper.find(user, id);
    if (value == null) throw new ApiException(404, "Lookup item not found");
    if (in.explanation() != null) value.setExplanation(in.explanation().strip());
    if (in.translation() != null) value.setTranslation(in.translation().strip());
    if (in.grammarAnalysis() != null) value.setGrammarAnalysis(in.grammarAnalysis().strip());
    if (in.readingStrategy() != null) value.setReadingStrategy(in.readingStrategy().strip());
    if (in.dictionary() != null) value.setDictionaryJson(serialize(in.dictionary()));
    mapper.enrich(value);
    return view(value);
  }

  @Transactional
  public void delete(String user, String id) {
    active(user);
    if (mapper.delete(user, id) == 0) throw new ApiException(404, "Lookup item not found");
  }

  public List<Map<String, Object>> list(
      String user, String book, String chapter, String type, int limit) {
    if (type != null
        && !Set.of("word", "phrase", "sentence", "grammar", "sentencePattern").contains(type))
      throw new ApiException(422, "Invalid lookup type");
    return mapper.list(user, book, chapter, type, limit).stream().map(this::view).toList();
  }

  public PageResult<Map<String, Object>> vocabulary(String user, int page, int size) {
    var query = new com.readquest.dto.PageQuery(page, size);
    var lookupItems = mapper.vocabulary(user, query.offset(), size);
    var bookDetails = books.getMany(user, lookupItems.stream().map(LookupItem::getBookId).toList());
    var mastery = reviews.masteryMany(user, lookupItems);
    List<Map<String, Object>> items = new ArrayList<>();
    for (LookupItem item : lookupItems) {
      Map<String, Object> row = view(item);
      row.put("normalizedText", item.getNormalizedText());
      Book book = bookDetails.get(item.getBookId());
      row.put(
          "book",
          Map.of(
              "id",
              item.getBookId(),
              "title",
              book == null ? "Unknown book" : book.getTitle(),
              "author",
              book == null ? "" : book.getAuthor()));
      row.put("mastery", mastery.get(item.getId()));
      items.add(row);
    }
    return PageResult.of(items, page, size, mapper.vocabularyCount(user));
  }

  public Map<String, Object> summary(String user) {
    return mapper.summary(user);
  }

  public List<Map<String, Object>> paragraphs(String user, String book, String chapter) {
    books.chapter(user, book, chapter);
    return mapper.paragraphs(user, book, chapter);
  }
}
