package com.readquest.pojo;

public class BookNote {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String value) {
    this.userId = value;
  }

  private String bookId;

  public String getBookId() {
    return bookId;
  }

  public void setBookId(String value) {
    this.bookId = value;
  }

  private String chapterId;

  public String getChapterId() {
    return chapterId;
  }

  public void setChapterId(String value) {
    this.chapterId = value;
  }

  private Integer paragraphIndex;

  public Integer getParagraphIndex() {
    return paragraphIndex;
  }

  public void setParagraphIndex(Integer value) {
    this.paragraphIndex = value;
  }

  private String quote;

  public String getQuote() {
    return quote;
  }

  public void setQuote(String value) {
    this.quote = value;
  }

  private String note;

  public String getNote() {
    return note;
  }

  public void setNote(String value) {
    this.note = value;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant value) {
    this.createdAt = value;
  }

  private java.time.Instant updatedAt;

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant value) {
    this.updatedAt = value;
  }
}
