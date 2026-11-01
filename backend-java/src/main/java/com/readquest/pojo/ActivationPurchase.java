package com.readquest.pojo;

public class ActivationPurchase {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value;
  }

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String value) {
    userId = value;
  }

  private String requestKey;

  public String getRequestKey() {
    return requestKey;
  }

  public void setRequestKey(String value) {
    requestKey = value;
  }

  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    email = value;
  }

  private String emailHash;

  public String getEmailHash() {
    return emailHash;
  }

  public void setEmailHash(String value) {
    emailHash = value;
  }

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  private String codeId;

  public String getCodeId() {
    return codeId;
  }

  public void setCodeId(String value) {
    codeId = value;
  }

  private String deliveryStatus;

  public String getDeliveryStatus() {
    return deliveryStatus;
  }

  public void setDeliveryStatus(String value) {
    deliveryStatus = value;
  }

  private String deliveryMode;

  public String getDeliveryMode() {
    return deliveryMode;
  }

  public void setDeliveryMode(String value) {
    deliveryMode = value;
  }

  private String sealedCode;

  public String getSealedCode() {
    return sealedCode;
  }

  public void setSealedCode(String value) {
    sealedCode = value;
  }

  private String leaseId;

  public String getLeaseId() {
    return leaseId;
  }

  public void setLeaseId(String value) {
    leaseId = value;
  }

  private int attempts;

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int value) {
    attempts = value;
  }

  private java.time.Instant leaseUntil;

  public java.time.Instant getLeaseUntil() {
    return leaseUntil;
  }

  public void setLeaseUntil(java.time.Instant value) {
    leaseUntil = value;
  }

  private java.time.Instant nextAttemptAt;

  public java.time.Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(java.time.Instant value) {
    nextAttemptAt = value;
  }

  private java.time.Instant codeExpiresAt;

  public java.time.Instant getCodeExpiresAt() {
    return codeExpiresAt;
  }

  public void setCodeExpiresAt(java.time.Instant value) {
    codeExpiresAt = value;
  }

  private java.time.Instant createdAt;

  public java.time.Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(java.time.Instant value) {
    createdAt = value;
  }

  private java.time.Instant updatedAt;

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant value) {
    updatedAt = value;
  }
}
