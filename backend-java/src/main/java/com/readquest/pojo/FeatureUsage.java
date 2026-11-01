package com.readquest.pojo;

public class FeatureUsage {
  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  private String requestId;

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String v) {
    requestId = v;
  }

  private String feature;

  public String getFeature() {
    return feature;
  }

  public void setFeature(String v) {
    feature = v;
  }

  private java.time.Instant occurredAt;

  public java.time.Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(java.time.Instant v) {
    occurredAt = v;
  }

  private String result;

  public String getResult() {
    return result;
  }

  public void setResult(String v) {
    result = v;
  }

  private int units;

  public int getUnits() {
    return units;
  }

  public void setUnits(int v) {
    units = v;
  }
}
