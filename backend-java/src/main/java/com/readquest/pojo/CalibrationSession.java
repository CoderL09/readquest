package com.readquest.pojo;

public class CalibrationSession {
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

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  private int currentStep;

  public int getCurrentStep() {
    return currentStep;
  }

  public void setCurrentStep(int v) {
    currentStep = v;
  }

  private int estimatedLexile;

  public int getEstimatedLexile() {
    return estimatedLexile;
  }

  public void setEstimatedLexile(int v) {
    estimatedLexile = v;
  }

  private String finalProfileJson;

  public String getFinalProfileJson() {
    return finalProfileJson;
  }

  public void setFinalProfileJson(String v) {
    finalProfileJson = v;
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
