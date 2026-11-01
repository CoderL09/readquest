package com.readquest.pojo;

public class ReadingProfile {
  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  private int currentLexile;

  public int getCurrentLexile() {
    return currentLexile;
  }

  public void setCurrentLexile(int v) {
    currentLexile = v;
  }

  private int comfortZoneMin;

  public int getComfortZoneMin() {
    return comfortZoneMin;
  }

  public void setComfortZoneMin(int v) {
    comfortZoneMin = v;
  }

  private int comfortZoneMax;

  public int getComfortZoneMax() {
    return comfortZoneMax;
  }

  public void setComfortZoneMax(int v) {
    comfortZoneMax = v;
  }

  private int iPlusOneMin;

  @com.fasterxml.jackson.annotation.JsonProperty("iPlusOneMin")
  public int getIPlusOneMin() {
    return iPlusOneMin;
  }

  public void setIPlusOneMin(int v) {
    iPlusOneMin = v;
  }

  private int iPlusOneMax;

  @com.fasterxml.jackson.annotation.JsonProperty("iPlusOneMax")
  public int getIPlusOneMax() {
    return iPlusOneMax;
  }

  public void setIPlusOneMax(int v) {
    iPlusOneMax = v;
  }

  private double totalReadingMinutes;

  public double getTotalReadingMinutes() {
    return totalReadingMinutes;
  }

  public void setTotalReadingMinutes(double v) {
    totalReadingMinutes = v;
  }

  private double averageLookupPerSentence;

  public double getAverageLookupPerSentence() {
    return averageLookupPerSentence;
  }

  public void setAverageLookupPerSentence(double v) {
    averageLookupPerSentence = v;
  }

  private double averageComprehensionScore;

  public double getAverageComprehensionScore() {
    return averageComprehensionScore;
  }

  public void setAverageComprehensionScore(double v) {
    averageComprehensionScore = v;
  }

  private double readingConfidence;

  public double getReadingConfidence() {
    return readingConfidence;
  }

  public void setReadingConfidence(double v) {
    readingConfidence = v;
  }

  private String lastAdjustmentReason;

  public String getLastAdjustmentReason() {
    return lastAdjustmentReason;
  }

  public void setLastAdjustmentReason(String v) {
    lastAdjustmentReason = v;
  }

  private java.time.Instant lastAdjustedAt;

  public java.time.Instant getLastAdjustedAt() {
    return lastAdjustedAt;
  }

  public void setLastAdjustedAt(java.time.Instant v) {
    lastAdjustedAt = v;
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
