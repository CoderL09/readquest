package com.readquest.pojo;

public class AiAnalysisCache {
  private String userId, contentHash, resultJson;
  private java.time.Instant expiresAt;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String v) {
    contentHash = v;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String v) {
    resultJson = v;
  }

  public java.time.Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.Instant v) {
    expiresAt = v;
  }
}
