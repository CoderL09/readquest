package com.readquest.vo;

import com.readquest.pojo.Book;
import com.readquest.pojo.LibraryEntry;
import java.time.Instant;

public record LibraryEntryView(
    String userId,
    String bookId,
    String shelfState,
    Instant createdAt,
    Instant updatedAt,
    Book book) {
  public static LibraryEntryView of(LibraryEntry entry, Book book) {
    return new LibraryEntryView(
        entry.getUserId(),
        entry.getBookId(),
        entry.getShelfState(),
        entry.getCreatedAt(),
        entry.getUpdatedAt(),
        book);
  }
}
