package com.readquest.pojo;

public class CalibrationSample {
  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String v) {
    sessionId = v;
  }

  private int step;

  public int getStep() {
    return step;
  }

  public void setStep(int v) {
    step = v;
  }

  private String sampleId;

  public String getSampleId() {
    return sampleId;
  }

  public void setSampleId(String v) {
    sampleId = v;
  }

  private int lexile;

  public int getLexile() {
    return lexile;
  }

  public void setLexile(int v) {
    lexile = v;
  }

  private String text;

  public String getText() {
    return text;
  }

  public void setText(String v) {
    text = v;
  }

  private java.time.Instant startedAt;

  public java.time.Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(java.time.Instant v) {
    startedAt = v;
  }

  private java.time.Instant finishedAt;

  public java.time.Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(java.time.Instant v) {
    finishedAt = v;
  }

  private Integer readingSeconds;

  public Integer getReadingSeconds() {
    return readingSeconds;
  }

  public void setReadingSeconds(Integer v) {
    readingSeconds = v;
  }

  private Integer lookupCount;

  public Integer getLookupCount() {
    return lookupCount;
  }

  public void setLookupCount(Integer v) {
    lookupCount = v;
  }

  private Double comprehensionScore;

  public Double getComprehensionScore() {
    return comprehensionScore;
  }

  public void setComprehensionScore(Double v) {
    comprehensionScore = v;
  }

  private Double flowScore;

  public Double getFlowScore() {
    return flowScore;
  }

  public void setFlowScore(Double v) {
    flowScore = v;
  }
}
