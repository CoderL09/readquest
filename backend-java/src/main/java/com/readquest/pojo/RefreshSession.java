package com.readquest.pojo;

public class RefreshSession {
  private String familyId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getFamilyId() {
    return familyId;
  }

  public void setFamilyId(String value) {
    familyId = value;
  }

  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String value) {
    this.userId = value;
  }

  private String tokenHash;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String value) {
    this.tokenHash = value;
  }

  private java.time.Instant expiresAt;

  public java.time.Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.Instant value) {
    this.expiresAt = value;
  }

  private java.time.Instant revokedAt;

  public java.time.Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(java.time.Instant value) {
    this.revokedAt = value;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant v) {
    createdAt = v;
  }
}
