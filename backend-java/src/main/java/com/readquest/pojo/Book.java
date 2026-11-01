package com.readquest.pojo;

public class Book {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  private String ownerId;

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String value) {
    this.ownerId = value;
  }

  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    this.title = value;
  }

  private String author;

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String value) {
    this.author = value;
  }

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  private String assetScope;

  public String getAssetScope() {
    return assetScope;
  }

  public void setAssetScope(String value) {
    this.assetScope = value;
  }

  private String visibility;

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(String value) {
    this.visibility = value;
  }

  private String moderationStatus;

  public String getModerationStatus() {
    return moderationStatus;
  }

  public void setModerationStatus(String value) {
    this.moderationStatus = value;
  }

  private long wordCount;

  public long getWordCount() {
    return wordCount;
  }

  public void setWordCount(long value) {
    this.wordCount = value;
  }

  private int totalChapters;

  public int getTotalChapters() {
    return totalChapters;
  }

  public void setTotalChapters(int value) {
    this.totalChapters = value;
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

  private String filePath;
  private String metadataJson;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String value) {
    metadataJson = value;
  }

  public Object getMetadata() {
    if (metadataJson == null) return java.util.Map.of();
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadataJson);
    } catch (Exception error) {
      throw new IllegalStateException("Invalid stored book metadata");
    }
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String value) {
    this.filePath = value;
  }

  private String fileHash;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getFileHash() {
    return fileHash;
  }

  public void setFileHash(String value) {
    this.fileHash = value;
  }

  private String coverUrl;

  public String getCoverUrl() {
    return coverUrl;
  }

  public void setCoverUrl(String value) {
    this.coverUrl = value;
  }

  private String readingMode;

  public String getReadingMode() {
    return readingMode;
  }

  public void setReadingMode(String value) {
    this.readingMode = value;
  }

  private int progress;

  public int getProgress() {
    return progress;
  }

  public void setProgress(int value) {
    this.progress = value;
  }

  private String errorMessage;

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String value) {
    this.errorMessage = value;
  }

  private java.time.Instant rightsAttestedAt;

  public java.time.Instant getRightsAttestedAt() {
    return rightsAttestedAt;
  }

  public void setRightsAttestedAt(java.time.Instant value) {
    this.rightsAttestedAt = value;
  }
}
