package com.readquest.service.impl;

import com.readquest.dto.Requests.Progress;
import com.readquest.exception.ApiException;
import com.readquest.mapper.ProgressMapper;
import com.readquest.pojo.ReadingProgress;
import com.readquest.service.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressServiceImpl implements ProgressService {
  private final ProgressMapper mapper;
  private final BookService books;
  private final ProfileService profiles;
  private final AccountWriteGuard guard;

  public ProgressServiceImpl(
      ProgressMapper m, BookService b, ProfileService profiles, AccountWriteGuard guard) {
    mapper = m;
    books = b;
    this.profiles = profiles;
    this.guard = guard;
  }

  public ReadingProgress get(String user, String book, String chapter) {
    books.chapter(user, book, chapter);
    ReadingProgress p = mapper.find(user, book, chapter);
    if (p == null) throw new ApiException(404, "Progress not found");
    return p;
  }

  @Transactional
  public ReadingProgress put(String user, String book, String chapter, Progress in) {
    guard.execute(user, () -> null);
    books.chapter(user, book, chapter);
    if (in.completedParagraphs() > in.totalParagraphs()
        || (in.totalParagraphs() > 0 && in.currentParagraphIndex() >= in.totalParagraphs())
        || (in.totalParagraphs() == 0 && in.currentParagraphIndex() != 0))
      throw new ApiException(422, "Invalid paragraph progress");
    ReadingProgress p = new ReadingProgress();
    p.setUserId(user);
    p.setBookId(book);
    p.setChapterId(chapter);
    p.setCurrentParagraphIndex(in.currentParagraphIndex());
    p.setCompletedParagraphs(in.completedParagraphs());
    p.setTotalParagraphs(in.totalParagraphs());
    p.setTotalReadingMs(in.totalReadingMs());
    p.setUpdatedAt(Instant.now());
    p.setCompletedAt(
        in.totalParagraphs() > 0 && in.completedParagraphs() == in.totalParagraphs()
            ? p.getUpdatedAt()
            : null);
    mapper.upsert(p);
    profiles.adjustAfterProgress(user);
    return p;
  }
}
