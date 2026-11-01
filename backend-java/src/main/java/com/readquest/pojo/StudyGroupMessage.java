package com.readquest.pojo;

import java.time.Instant;

public class StudyGroupMessage {
  private long sequence;
  private String id, bookId, senderUserId, body, clientMessageId, deletedByUserId, deletionReason;
  private Instant deletedAt, createdAt, updatedAt;

  public long getSequence() {
    return sequence;
  }

  public void setSequence(long v) {
    sequence = v;
  }

  public String getId() {
    return id;
  }

  public void setId(String v) {
    id = v;
  }

  public String getBookId() {
    return bookId;
  }

  public void setBookId(String v) {
    bookId = v;
  }

  public String getSenderUserId() {
    return senderUserId;
  }

  public void setSenderUserId(String v) {
    senderUserId = v;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String v) {
    body = v;
  }

  public String getClientMessageId() {
    return clientMessageId;
  }

  public void setClientMessageId(String v) {
    clientMessageId = v;
  }

  public String getDeletedByUserId() {
    return deletedByUserId;
  }

  public void setDeletedByUserId(String v) {
    deletedByUserId = v;
  }

  public String getDeletionReason() {
    return deletionReason;
  }

  public void setDeletionReason(String v) {
    deletionReason = v;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant v) {
    deletedAt = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant v) {
    createdAt = v;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant v) {
    updatedAt = v;
  }
}
