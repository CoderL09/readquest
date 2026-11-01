package com.readquest.service.impl;

import com.readquest.mapper.LibraryMapper;
import com.readquest.pojo.LibraryEntry;
import com.readquest.service.*;
import com.readquest.vo.PageResult;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryServiceImpl implements LibraryService {
  private final LibraryMapper mapper;
  private final BookService books;
  private final AccountWriteGuard guard;

  public LibraryServiceImpl(LibraryMapper m, BookService b, AccountWriteGuard guard) {
    mapper = m;
    books = b;
    this.guard = guard;
  }

  public PageResult<com.readquest.vo.LibraryEntryView> list(String user, int page, int size) {
    var query = new com.readquest.dto.PageQuery(page, size);
    var entries = mapper.list(user, query.offset(), size);
    var details = books.getMany(user, entries.stream().map(LibraryEntry::getBookId).toList());
    return PageResult.of(
        entries.stream()
            .map(entry ->
                com.readquest.vo.LibraryEntryView.of(entry, details.get(entry.getBookId())))
            .toList(),
        page,
        size,
        mapper.count(user));
  }

  @Transactional
  public LibraryEntry put(String user, String book, String state) {
    guard.execute(user, () -> null);
    books.requireReadable(user, book);
    LibraryEntry e = new LibraryEntry();
    e.setUserId(user);
    e.setBookId(book);
    e.setShelfState(state);
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(e.getCreatedAt());
    mapper.upsert(e);
    return e;
  }

  @Transactional
  public void delete(String user, String book) {
    guard.execute(user, () -> null);
    mapper.delete(user, book);
  }
}
