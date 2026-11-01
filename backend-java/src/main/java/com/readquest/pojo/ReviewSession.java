package com.readquest.pojo;

public class ReviewSession {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  private String activeOwner;

  public String getActiveOwner() {
    return activeOwner;
  }

  public void setActiveOwner(String v) {
    activeOwner = v;
  }

  private int groupSize;

  public int getGroupSize() {
    return groupSize;
  }

  public void setGroupSize(int v) {
    groupSize = v;
  }

  private int currentIndex;

  public int getCurrentIndex() {
    return currentIndex;
  }

  public void setCurrentIndex(int v) {
    currentIndex = v;
  }

  private int total;

  public int getTotal() {
    return total;
  }

  public void setTotal(int v) {
    total = v;
  }

  private String state;

  public String getState() {
    return state;
  }

  public void setState(String v) {
    state = v;
  }

  private String currentStage;

  public String getCurrentStage() {
    return currentStage;
  }

  public void setCurrentStage(String v) {
    currentStage = v;
  }

  private String currentAttemptId;

  public String getCurrentAttemptId() {
    return currentAttemptId;
  }

  public void setCurrentAttemptId(String v) {
    currentAttemptId = v;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant v) {
    createdAt = v;
  }

  private java.time.Instant updatedAt;

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant v) {
    updatedAt = v;
  }
}
