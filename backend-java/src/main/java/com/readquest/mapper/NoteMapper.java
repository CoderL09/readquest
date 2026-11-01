package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface NoteMapper {
  java.util.List<com.readquest.vo.BookNoteView> list(
      @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

  long count(String userId);

  BookNote find(@Param("userId") String userId, @Param("id") String id);

  com.readquest.vo.BookNoteView findView(@Param("userId") String userId, @Param("id") String id);

  int insert(BookNote note);

  int update(@Param("userId") String userId, @Param("id") String id, @Param("note") String note);

  int delete(@Param("userId") String userId, @Param("id") String id);
}
