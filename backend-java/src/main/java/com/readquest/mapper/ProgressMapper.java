package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface ProgressMapper {
  @org.apache.ibatis.annotations.Select(
      "SELECT * FROM reading_progress WHERE user_id=#{user} AND book_id=#{book} ORDER BY updated_at DESC LIMIT 10")
  java.util.List<ReadingProgress> recent(@Param("user") String user, @Param("book") String book);

  ReadingProgress find(
      @Param("userId") String userId,
      @Param("bookId") String bookId,
      @Param("chapterId") String chapterId);

  int upsert(ReadingProgress progress);
}
