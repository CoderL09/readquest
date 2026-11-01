package com.readquest.pojo;

public class LibraryEntry {
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

  private String shelfState;

  public String getShelfState() {
    return shelfState;
  }

  public void setShelfState(String value) {
    this.shelfState = value;
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
