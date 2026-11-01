package com.readquest.pojo;

public class User {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    this.id = value;
  }

  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    this.email = value;
  }

  private String displayName;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String value) {
    this.displayName = value;
  }

  private String avatarUrl;

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String value) {
    this.avatarUrl = value;
  }

  private String passwordHash;

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String value) {
    this.passwordHash = value;
  }

  private String role;

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    this.role = value;
  }

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
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
}
