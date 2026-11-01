package com.readquest.mapper;

import com.readquest.pojo.*;
import com.readquest.vo.GroupProgress;
import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface StudyGroupMapper {
  Book lockBook(String id);

  List<String> joined(String user);

  List<String> owned(String user);

  StudyGroupMember member(@Param("book") String book, @Param("user") String user);

  StudyGroupMember memberAny(@Param("book") String book, @Param("user") String user);

  List<StudyGroupMember> members(
      @Param("book") String book, @Param("offset") int offset, @Param("limit") int limit);

  long countMembers(String book);

  int insertMember(StudyGroupMember member);

  int role(@Param("book") String book, @Param("user") String user, @Param("role") String role);

  int mute(
      @Param("book") String book,
      @Param("user") String user,
      @Param("until") java.time.Instant until);

  int removeMember(@Param("book") String book, @Param("user") String user);

  long adminCount(String book);

  List<GroupProgress> progress(@Param("book") String book, @Param("users") List<String> users);

  long totalChapters(String book);

  StudyGroupMessage message(@Param("book") String book, @Param("id") String id);

  StudyGroupMessage submission(
      @Param("book") String book, @Param("user") String user, @Param("client") String client);

  int insertMessage(StudyGroupMessage message);

  int assignMessageId(StudyGroupMessage message);

  List<StudyGroupMessage> messages(
      @Param("book") String book,
      @Param("user") String user,
      @Param("before") Long before,
      @Param("after") Long after,
      @Param("limit") int limit);

  int deleteMessage(
      @Param("book") String book,
      @Param("id") String id,
      @Param("user") String user,
      @Param("reason") String reason);

  int block(@Param("user") String user, @Param("target") String target);

  int unblock(@Param("user") String user, @Param("target") String target);

  boolean blocked(@Param("user") String user, @Param("target") String target);

  int report(ContentReport report);

  int audit(StudyGroupAudit audit);

  int event(@Param("book") String book, @Param("message") String message);

  int memberEvents(@Param("book") String book, @Param("user") String user);

  long latestEvent(String book);

  List<StudyGroupEvent> events(@Param("book") String book, @Param("after") long after);

  int deleteBlocks(String user);

  int deleteReports(String user);

  int anonymizeMessages(@Param("user") String user, @Param("alias") String alias);

  int anonymizeDeleters(@Param("user") String user, @Param("alias") String alias);

  int anonymizeAudits(@Param("user") String user, @Param("alias") String alias);

  int anonymizeTargets(@Param("user") String user, @Param("alias") String alias);

  int anonymizeReports(@Param("user") String user, @Param("alias") String alias);
}
