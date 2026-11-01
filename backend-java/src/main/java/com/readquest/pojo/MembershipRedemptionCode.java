package com.readquest.pojo;

public class MembershipRedemptionCode {
  private String boundUserId;

  public String getBoundUserId() {
    return boundUserId;
  }

  public void setBoundUserId(String value) {
    boundUserId = value;
  }

  private String id;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  private String codeHash;

  public String getCodeHash() {
    return codeHash;
  }

  public void setCodeHash(String v) {
    codeHash = v;
  }

  private String codeSuffix;

  public String getCodeSuffix() {
    return codeSuffix;
  }

  public void setCodeSuffix(String v) {
    codeSuffix = v;
  }

  private String label;

  public String getLabel() {
    return label;
  }

  public void setLabel(String v) {
    label = v;
  }

  private Integer durationDays;

  public Integer getDurationDays() {
    return durationDays;
  }

  public void setDurationDays(Integer v) {
    durationDays = v;
  }

  private java.time.Instant expiresAt;

  public java.time.Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(java.time.Instant v) {
    expiresAt = v;
  }

  private java.time.Instant redeemedAt;

  public java.time.Instant getRedeemedAt() {
    return redeemedAt;
  }

  public void setRedeemedAt(java.time.Instant v) {
    redeemedAt = v;
  }

  private String redeemedByUserId;

  public String getRedeemedByUserId() {
    return redeemedByUserId;
  }

  public void setRedeemedByUserId(String v) {
    redeemedByUserId = v;
  }

  private java.time.Instant revokedAt;

  public java.time.Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(java.time.Instant v) {
    revokedAt = v;
  }
}
