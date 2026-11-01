package com.readquest.service.impl;

import com.readquest.dto.ReadingEventInput;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.text.BreakIterator;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingServiceImpl implements ReadingService {
  private final ReadingMapper mapper;
  private final BookService books;
  private final ProgressMapper progress;
  private final ProfileService profiles;

  public ReadingServiceImpl(
      ReadingMapper m, BookService b, ProgressMapper p, ProfileService profiles) {
    mapper = m;
    books = b;
    progress = p;
    this.profiles = profiles;
  }

  private Chapter readable(String user, String id) {
    Chapter c = mapper.chapter(id);
    if (c == null) throw new ApiException(404, "Chapter not found");
    books.requireReadable(user, c.getBookId());
    return c;
  }

  private List<String> split(String text) {
    var iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
    iterator.setText(text);
    List<String> result = new ArrayList<>();
    int start = iterator.first();
    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
      String s = text.substring(start, end).strip();
      if (!s.isBlank()) result.add(s);
    }
    return result;
  }

  public Map<String, Object> structure(String user, String id) {
    Chapter c = readable(user, id);
    List<Map<String, Object>> paragraphs = new ArrayList<>();
    String text = Objects.requireNonNullElse(c.getContentText(), c.getContent());
    int pi = 0;
    for (String paragraph : text.split("\\n\\s*\\n")) {
      if (paragraph.isBlank()) continue;
      List<Map<String, Object>> sentences = new ArrayList<>();
      int si = 0;
      String paragraphId = id + ":p" + pi;
      for (String sentence : split(paragraph)) {
        String sid = paragraphId + ":s" + si;
        sentences.add(
            Map.of(
                "id",
                sid,
                "sentenceIndex",
                si++,
                "text",
                sentence,
                "chunks",
                List.of(
                    Map.of(
                        "id",
                        sid + ":c0",
                        "chunkIndex",
                        0,
                        "text",
                        sentence,
                        "role",
                        "supporting",
                        "isCoreMeaning",
                        false))));
      }
      paragraphs.add(
          Map.of(
              "id",
              paragraphId,
              "paragraphIndex",
              pi++,
              "text",
              paragraph.strip(),
              "sentenceCount",
              sentences.size(),
              "sentences",
              sentences));
    }
    return Map.of(
        "stage",
        Map.of(
            "id",
            c.getId(),
            "bookId",
            c.getBookId(),
            "chapterIndex",
            c.getChapterIndex(),
            "stageTitle",
            Objects.requireNonNullElse(c.getStageTitle(), ""),
            "stageSummary",
            Objects.requireNonNullElse(c.getStageSummary(), "")),
        "paragraphs",
        paragraphs,
        "analysisSource",
        "local-text");
  }

  public Map<String, Object> sentences(String user, String id) {
    Chapter c = readable(user, id);
    return Map.of(
        "sentences",
        split(c.getContent()).stream()
            .map(s -> Map.of("originalSentence", s, "chunks", List.of(s)))
            .toList(),
        "analysisSource",
        "local-text");
  }

  @Transactional
  public ReadingProgress record(String user, String book, String chapter, ReadingEventInput in) {
    if (mapper.lockUser(user) == null) throw new ApiException(403, "Account is not active");
    books.chapter(user, book, chapter);
    if (in.completedParagraphs() > in.totalParagraphs()
        || (in.totalParagraphs() > 0 && in.currentParagraphIndex() >= in.totalParagraphs())
        || (in.totalParagraphs() == 0 && in.currentParagraphIndex() != 0))
      throw new ApiException(422, "Invalid paragraph progress");
    ReadingEvent existing = mapper.event(user, in.eventId());
    if (existing != null) {
      if (!book.equals(existing.getBookId())
          || !chapter.equals(existing.getChapterId())
          || existing.getReadingDurationMs() != in.readingDurationMs()
          || existing.getCurrentParagraphIndex() != in.currentParagraphIndex()
          || existing.getCompletedParagraphs() != in.completedParagraphs()
          || existing.getTotalParagraphs() != in.totalParagraphs())
        throw new ApiException(409, "Event ID already used with different input");
      return progress.find(user, book, chapter);
    }
    ReadingEvent e = new ReadingEvent();
    e.setUserId(user);
    e.setBookId(book);
    e.setChapterId(chapter);
    e.setEventId(in.eventId());
    e.setCurrentParagraphIndex(in.currentParagraphIndex());
    e.setCompletedParagraphs(in.completedParagraphs());
    e.setTotalParagraphs(in.totalParagraphs());
    e.setReadingDurationMs(in.readingDurationMs());
    mapper.insertEvent(e);
    mapper.mergeProgress(e);
    profiles.adjustAfterProgress(user);
    return progress.find(user, book, chapter);
  }
}
