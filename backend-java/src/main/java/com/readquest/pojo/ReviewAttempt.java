package com.readquest.pojo;

public class ReviewAttempt {
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

  private String sessionId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String v) {
    sessionId = v;
  }

  private String cardId;

  public String getCardId() {
    return cardId;
  }

  public void setCardId(String v) {
    cardId = v;
  }

  private int occurrenceIndex;

  public int getOccurrenceIndex() {
    return occurrenceIndex;
  }

  public void setOccurrenceIndex(int v) {
    occurrenceIndex = v;
  }

  private String answerKind;

  public String getAnswerKind() {
    return answerKind;
  }

  public void setAnswerKind(String v) {
    answerKind = v;
  }

  private String stage;

  public String getStage() {
    return stage;
  }

  public void setStage(String v) {
    stage = v;
  }

  private String userAnswer;

  public String getUserAnswer() {
    return userAnswer;
  }

  public void setUserAnswer(String v) {
    userAnswer = v;
  }

  private long responseTimeMs;

  public long getResponseTimeMs() {
    return responseTimeMs;
  }

  public void setResponseTimeMs(long v) {
    responseTimeMs = v;
  }

  private String idempotencyKey;

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String v) {
    idempotencyKey = v;
  }

  private String gradingState;

  public String getGradingState() {
    return gradingState;
  }

  public void setGradingState(String v) {
    gradingState = v;
  }

  private String gradeJson;

  public String getGradeJson() {
    return gradeJson;
  }

  public void setGradeJson(String v) {
    gradeJson = v;
  }

  private String failureCode;

  public String getFailureCode() {
    return failureCode;
  }

  public void setFailureCode(String v) {
    failureCode = v;
  }

  private boolean ratingApplied;

  public boolean getRatingApplied() {
    return ratingApplied;
  }

  public void setRatingApplied(boolean v) {
    ratingApplied = v;
  }

  private String derivedRating;

  public String getDerivedRating() {
    return derivedRating;
  }

  public void setDerivedRating(String v) {
    derivedRating = v;
  }

  private String nextStage;

  public String getNextStage() {
    return nextStage;
  }

  public void setNextStage(String v) {
    nextStage = v;
  }

  private String referenceJson;

  public String getReferenceJson() {
    return referenceJson;
  }

  public void setReferenceJson(String v) {
    referenceJson = v;
  }

  private long cardVersion;

  public long getCardVersion() {
    return cardVersion;
  }

  public void setCardVersion(long v) {
    cardVersion = v;
  }

  private java.time.Instant leaseExpiresAt;

  public java.time.Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public void setLeaseExpiresAt(java.time.Instant v) {
    leaseExpiresAt = v;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant v) {
    createdAt = v;
  }

  private String semanticVerdict;
  private Double semanticScore;

  public String getSemanticVerdict() {
    return semanticVerdict;
  }

  public void setSemanticVerdict(String v) {
    semanticVerdict = v;
  }

  public Double getSemanticScore() {
    return semanticScore;
  }

  public void setSemanticScore(Double v) {
    semanticScore = v;
  }
}
