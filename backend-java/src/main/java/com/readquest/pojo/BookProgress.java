package com.readquest.pojo;

public class BookProgress {
  private String bookId;

  public String getBookId() {
    return bookId;
  }

  public void setBookId(String v) {
    bookId = v;
  }

  private long completedParagraphs;

  public long getCompletedParagraphs() {
    return completedParagraphs;
  }

  public void setCompletedParagraphs(long v) {
    completedParagraphs = v;
  }

  private long totalParagraphs;

  public long getTotalParagraphs() {
    return totalParagraphs;
  }

  public void setTotalParagraphs(long v) {
    totalParagraphs = v;
  }

  private long totalReadingMs;

  public long getTotalReadingMs() {
    return totalReadingMs;
  }

  public void setTotalReadingMs(long v) {
    totalReadingMs = v;
  }

  private java.time.Instant updatedAt;

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant v) {
    updatedAt = v;
  }
}
