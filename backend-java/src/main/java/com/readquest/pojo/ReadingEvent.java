package com.readquest.pojo;

public class ReadingEvent {
  private String userId, eventId, bookId, chapterId;
  private int currentParagraphIndex, completedParagraphs, totalParagraphs;
  private long readingDurationMs;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String v) {
    eventId = v;
  }

  public String getBookId() {
    return bookId;
  }

  public void setBookId(String v) {
    bookId = v;
  }

  public String getChapterId() {
    return chapterId;
  }

  public void setChapterId(String v) {
    chapterId = v;
  }

  public int getCurrentParagraphIndex() {
    return currentParagraphIndex;
  }

  public void setCurrentParagraphIndex(int v) {
    currentParagraphIndex = v;
  }

  public int getCompletedParagraphs() {
    return completedParagraphs;
  }

  public void setCompletedParagraphs(int v) {
    completedParagraphs = v;
  }

  public int getTotalParagraphs() {
    return totalParagraphs;
  }

  public void setTotalParagraphs(int v) {
    totalParagraphs = v;
  }

  public long getReadingDurationMs() {
    return readingDurationMs;
  }

  public void setReadingDurationMs(long v) {
    readingDurationMs = v;
  }
}
