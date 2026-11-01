package com.readquest.service;

import java.time.Instant;
import java.util.*;

public interface StudyGroupService {
  List<Map<String, Object>> joined(String user);

  Map<String, Object> summary(String user, String book);

  Map<String, Object> join(String user, String book);

  void leave(String user, String book);

  Map<String, Object> members(String user, String book, int page, int size);

  Map<String, Object> member(String user, String book, String target);

  Map<String, Object> messages(String user, String book, String before, String after, int limit);

  Map<String, Object> send(String user, String book, String body, String clientMessageId);

  void moderate(
      String user,
      String book,
      String target,
      String role,
      Instant mutedUntil,
      boolean changeMute,
      String reason);

  void removeMember(String user, String book, String target, String reason);

  void deleteMessage(String user, String book, String id, String reason);

  void block(String user, String book, String target);

  void unblock(String user, String target);

  Map<String, Object> report(
      String user,
      String book,
      String type,
      String targetMessage,
      String targetUser,
      String reason,
      String details);

  Map<String, Object> events(String user, String book, String after);

  void assertMember(String user, String book);

  void cleanupAccount(String user, String alias);
}
