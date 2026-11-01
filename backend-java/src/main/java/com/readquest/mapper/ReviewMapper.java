package com.readquest.mapper;

import com.readquest.pojo.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface ReviewMapper {
  ReviewCard conceptCard(
      @Param("user") String user,
      @Param("book") String book,
      @Param("fingerprint") String fingerprint);

  List<ReviewAttempt> evidence(@Param("user") String user, @Param("card") String card);

  List<ReviewCard> lookupCards(@Param("user") String user, @Param("lookups") List<String> lookups);

  List<ReviewAttempt> evidenceMany(@Param("user") String user, @Param("cards") List<String> cards);

  int insertCard(ReviewCard card);

  ReviewCard lookupCard(@Param("user") String user, @Param("lookup") String lookup);

  ReviewCard card(@Param("user") String user, @Param("id") String id);

  List<ReviewCard> due(@Param("user") String user, @Param("limit") int limit);

  long dueCount(String user);

  Instant nextDue(String user);

  List<ReviewCard> library(
      @Param("user") String user,
      @Param("kind") String kind,
      @Param("offset") int offset,
      @Param("limit") int limit);

  long libraryCount(@Param("user") String user, @Param("kind") String kind);

  ReviewSession active(String user);

  ReviewSession session(@Param("user") String user, @Param("id") String id);

  int insertSession(ReviewSession session);

  int saveSession(ReviewSession session);

  int insertSessionCard(
      @Param("session") String session,
      @Param("position") int position,
      @Param("card") String card);

  String sessionCard(@Param("session") String session, @Param("position") int position);

  int insertAttempt(ReviewAttempt attempt);

  ReviewAttempt attempt(@Param("user") String user, @Param("key") String key);

  ReviewAttempt attemptById(@Param("user") String user, @Param("id") String id);

  ReviewAttempt initialExplanation(
      @Param("user") String user,
      @Param("session") String session,
      @Param("position") int position);

  int saveAttempt(ReviewAttempt attempt);

  int saveRating(ReviewCard card);

  int syncLookup(
      @Param("id") String id,
      @Param("user") String user,
      @Param("due") Instant due,
      @Param("status") String status,
      @Param("mastery") int mastery);

  ReviewProgression progression(@Param("user") String user, @Param("event") String event);

  int insertProgression(ReviewProgression value);
}
