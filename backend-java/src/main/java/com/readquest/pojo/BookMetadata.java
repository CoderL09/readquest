package com.readquest.pojo;

public class BookMetadata {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  private String title;

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  private String author;

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String v) {
    author = v;
  }

  private String cover;

  public String getCover() {
    return cover;
  }

  public void setCover(String v) {
    cover = v;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  private int lexile;

  public int getLexile() {
    return lexile;
  }

  public void setLexile(int v) {
    lexile = v;
  }

  private String difficultyLevel;

  public String getDifficultyLevel() {
    return difficultyLevel;
  }

  public void setDifficultyLevel(String v) {
    difficultyLevel = v;
  }

  private String genre;

  public String getGenre() {
    return genre;
  }

  public void setGenre(String v) {
    genre = v;
  }

  private String sourceUrl;

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String v) {
    sourceUrl = v;
  }

  private String downloadStatus;

  public String getDownloadStatus() {
    return downloadStatus;
  }

  public void setDownloadStatus(String v) {
    downloadStatus = v;
  }

  private String linkedBookId;

  public String getLinkedBookId() {
    return linkedBookId;
  }

  public void setLinkedBookId(String v) {
    linkedBookId = v;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant v) {
    createdAt = v;
  }
}
