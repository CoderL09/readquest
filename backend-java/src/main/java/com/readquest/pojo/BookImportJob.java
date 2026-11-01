package com.readquest.pojo;

import java.time.Instant;

public class BookImportJob {
  private String id, userId, bookId, status, runId, errorMessage;
  private int attempts;
  private String phase = "queued";
  private int progress;

  public String getPhase() {
    return phase;
  }

  public void setPhase(String value) {
    phase = value;
  }

  public int getProgress() {
    return progress;
  }

  public void setProgress(int value) {
    progress = value;
  }

  private Instant leaseExpiresAt, createdAt, updatedAt;
  private String readingMode = "continuous";
  private int wordsPerQuest = 500;

  public String getReadingMode() {
    return readingMode;
  }

  public void setReadingMode(String value) {
    readingMode = value;
  }

  public int getWordsPerQuest() {
    return wordsPerQuest;
  }

  public void setWordsPerQuest(int value) {
    wordsPerQuest = value;
  }

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  public String getBookId() {
    return bookId;
  }

  public void setBookId(String v) {
    bookId = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getRunId() {
    return runId;
  }

  public void setRunId(String v) {
    runId = v;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String v) {
    errorMessage = v;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int v) {
    attempts = v;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public void setLeaseExpiresAt(Instant v) {
    leaseExpiresAt = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant v) {
    createdAt = v;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant v) {
    updatedAt = v;
  }
}
