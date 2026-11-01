package com.readquest.pojo;

/** Stored run payloads are private user data, never application log fields. */
public class AgentRun {
  public String id;
  public String userId;
  public String requestKey;
  public String inputHash;
  public String bookId;
  public String state;
  public int groupSize;
  public String resultJson;
  public String eventsJson;
  public String sessionJson;
  public String errorCode;
  public java.time.Instant createdAt;
}
