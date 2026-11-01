package com.readquest.service.impl;

import com.readquest.dto.PageQuery;
import com.readquest.dto.Requests.*;
import com.readquest.exception.ApiException;
import com.readquest.mapper.NoteMapper;
import com.readquest.pojo.BookNote;
import com.readquest.service.*;
import com.readquest.vo.BookNoteView;
import com.readquest.vo.PageResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteServiceImpl implements NoteService {
  private final NoteMapper mapper;
  private final BookService books;
  private final AccountWriteGuard guard;

  public NoteServiceImpl(NoteMapper m, BookService b, AccountWriteGuard guard) {
    mapper = m;
    books = b;
    this.guard = guard;
  }

  public PageResult<BookNoteView> list(String user, int page, int size) {
    PageQuery query = new PageQuery(page, size);
    return PageResult.of(mapper.list(user, query.offset(), size), page, size, mapper.count(user));
  }

  public BookNoteView get(String user, String id) {
    BookNoteView n = mapper.findView(user, id);
    if (n == null) throw new ApiException(404, "Note not found");
    return n;
  }

  @Transactional
  public BookNoteView create(String user, CreateNote in) {
    guard.execute(user, () -> null);
    books.requireReadable(user, in.bookId());
    if (in.chapterId() != null) books.chapter(user, in.bookId(), in.chapterId());
    BookNote n = new BookNote();
    n.setId(UUID.randomUUID().toString());
    n.setUserId(user);
    n.setBookId(in.bookId());
    n.setChapterId(in.chapterId());
    n.setParagraphIndex(in.paragraphIndex());
    n.setQuote(in.quote().trim());
    n.setNote(in.note() == null ? "" : in.note().trim());
    n.setCreatedAt(Instant.now());
    n.setUpdatedAt(n.getCreatedAt());
    mapper.insert(n);
    return get(user, n.getId());
  }

  @Transactional
  public BookNoteView update(String user, String id, UpdateNote in) {
    guard.execute(user, () -> null);
    if (mapper.find(user, id) == null) throw new ApiException(404, "Note not found");
    mapper.update(user, id, in.note().trim());
    BookNoteView n = mapper.findView(user, id);
    if (n == null) throw new ApiException(404, "Note not found");
    return n;
  }

  @Transactional
  public void delete(String user, String id) {
    guard.execute(user, () -> null);
    if (mapper.delete(user, id) == 0) throw new ApiException(404, "Note not found");
  }
}
