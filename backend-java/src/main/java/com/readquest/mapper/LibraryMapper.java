package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface LibraryMapper {
  java.util.List<LibraryEntry> list(
      @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

  long count(String userId);

  int upsert(LibraryEntry entry);

  int delete(@Param("userId") String userId, @Param("bookId") String bookId);
}
