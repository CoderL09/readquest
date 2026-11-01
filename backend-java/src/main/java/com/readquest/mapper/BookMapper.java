package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface BookMapper {
  java.util.List<com.readquest.pojo.Book> accessibleMany(
      @org.apache.ibatis.annotations.Param("userId") String userId,
      @org.apache.ibatis.annotations.Param("ids") java.util.Collection<String> ids);

  Book findById(String id);

  java.util.List<Book> list(
      @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

  long count(String userId);

  java.util.List<Book> uploads(
      @Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);

  long uploadsCount(String userId);

  java.util.List<Chapter> chapters(String bookId);

  Chapter chapter(@Param("bookId") String bookId, @Param("id") String id);
}
