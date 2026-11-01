package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.BookMapper;
import com.readquest.pojo.*;
import com.readquest.service.BookService;
import com.readquest.vo.PageResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BookServiceImpl implements BookService {
  private final BookMapper mapper;
  private final com.readquest.assets.SignedBookHtml html;

  public BookServiceImpl(BookMapper mapper, com.readquest.assets.SignedBookHtml html) {
    this.mapper = mapper;
    this.html = html;
  }

  public PageResult<Book> list(String user, int page, int size) {
    var query = new com.readquest.dto.PageQuery(page, size);
    return PageResult.of(
        mapper.list(user, query.offset(), size).stream()
            .map(book -> html.book(user, book))
            .toList(),
        page,
        size,
        mapper.count(user));
  }

  public java.util.Map<String, Book> getMany(String user, java.util.Collection<String> ids) {
    if (ids.isEmpty()) return java.util.Map.of();
    if (ids.size() > 100)
      throw new ApiException(422, "BATCH_TOO_LARGE", "At most 100 book IDs are allowed");
    return mapper.accessibleMany(user, new java.util.LinkedHashSet<>(ids)).stream()
        .map(book -> html.book(user, book))
        .collect(
            java.util.stream.Collectors.toMap(Book::getId, java.util.function.Function.identity()));
  }

  public PageResult<Book> uploads(String user, int page, int size) {
    var query = new com.readquest.dto.PageQuery(page, size);
    return PageResult.of(
        mapper.uploads(user, query.offset(), size).stream()
            .map(book -> html.book(user, book))
            .toList(),
        page,
        size,
        mapper.uploadsCount(user));
  }

  private boolean published(Book b) {
    return b != null
        && "completed".equals(b.getStatus())
        && "public".equals(b.getVisibility())
        && List.of("unreviewed", "approved").contains(b.getModerationStatus())
        && List.of("private", "verified-shared").contains(b.getAssetScope());
  }

  public Book get(String user, String id) {
    Book b = mapper.findById(id);
    if (b == null
        || !(published(b) || (user.equals(b.getOwnerId()) && !"removed".equals(b.getVisibility()))))
      throw new ApiException(404, "Book not found");
    return html.book(user, b);
  }

  public Book requireReadable(String user, String id) {
    Book b = mapper.findById(id);
    if (!published(b)) throw new ApiException(404, "Book not found");
    return b;
  }

  public List<Chapter> chapters(String user, String id) {
    requireReadable(user, id);
    var all = mapper.chapters(id);
    return all.stream().map(chapter -> html.chapter(user, chapter, all)).toList();
  }

  public Chapter chapter(String user, String book, String id) {
    requireReadable(user, book);
    Chapter c = mapper.chapter(book, id);
    if (c == null) throw new ApiException(404, "Chapter not found");
    return html.chapter(user, c, mapper.chapters(book));
  }
}
