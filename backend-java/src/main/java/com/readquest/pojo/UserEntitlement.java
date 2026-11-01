package com.readquest.pojo;

public class UserEntitlement {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  private String sourceOrderNo;

  public String getSourceOrderNo() {
    return sourceOrderNo;
  }

  public void setSourceOrderNo(String v) {
    sourceOrderNo = v;
  }

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String v) {
    userId = v;
  }

  private String code;

  public String getCode() {
    return code;
  }

  public void setCode(String v) {
    code = v;
  }

  private String label;

  public String getLabel() {
    return label;
  }

  public void setLabel(String v) {
    label = v;
  }

  private java.time.Instant startsAt;

  public java.time.Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(java.time.Instant v) {
    startsAt = v;
  }

  private java.time.Instant endsAt;

  public java.time.Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(java.time.Instant v) {
    endsAt = v;
  }
}
