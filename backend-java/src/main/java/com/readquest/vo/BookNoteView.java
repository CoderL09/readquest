package com.readquest.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.readquest.pojo.BookNote;

/** A note and its authorized display metadata, loaded by one user-scoped query. */
public class BookNoteView extends BookNote {
  private String bookTitle;
  private String bookAuthor;
  private String chapterTitle;

  public record BookReference(String id, String title, String author) {}

  public record ChapterReference(String id, String title) {}

  public BookReference getBook() {
    return new BookReference(
        getBookId(),
        bookTitle == null ? "Unknown book" : bookTitle,
        bookAuthor == null ? "" : bookAuthor);
  }

  public ChapterReference getChapter() {
    return new ChapterReference(
        getChapterId(),
        chapterTitle == null || chapterTitle.isBlank() ? "Book note" : chapterTitle);
  }

  @JsonIgnore
  public String getBookTitle() {
    return bookTitle;
  }

  public void setBookTitle(String value) {
    bookTitle = value;
  }

  @JsonIgnore
  public String getBookAuthor() {
    return bookAuthor;
  }

  public void setBookAuthor(String value) {
    bookAuthor = value;
  }

  @JsonIgnore
  public String getChapterTitle() {
    return chapterTitle;
  }

  public void setChapterTitle(String value) {
    chapterTitle = value;
  }
}
