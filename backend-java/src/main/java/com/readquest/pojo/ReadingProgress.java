package com.readquest.pojo;

public class ReadingProgress {
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

  private int currentParagraphIndex;

  public int getCurrentParagraphIndex() {
    return currentParagraphIndex;
  }

  public void setCurrentParagraphIndex(int value) {
    this.currentParagraphIndex = value;
  }

  private int completedParagraphs;

  public int getCompletedParagraphs() {
    return completedParagraphs;
  }

  public void setCompletedParagraphs(int value) {
    this.completedParagraphs = value;
  }

  private int totalParagraphs;

  public int getTotalParagraphs() {
    return totalParagraphs;
  }

  public void setTotalParagraphs(int value) {
    this.totalParagraphs = value;
  }

  private long totalReadingMs;

  public long getTotalReadingMs() {
    return totalReadingMs;
  }

  public void setTotalReadingMs(long value) {
    this.totalReadingMs = value;
  }

  private java.time.Instant completedAt;

  public java.time.Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(java.time.Instant value) {
    this.completedAt = value;
  }

  private java.time.Instant updatedAt;

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant value) {
    this.updatedAt = value;
  }
}
