package com.readquest.mapper;

import com.readquest.pojo.LookupItem;
import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface LookupMapper {
  LookupItem occurrence(@Param("user") String user, @Param("key") String key);

  LookupItem find(@Param("user") String user, @Param("id") String id);

  List<LookupItem> list(
      @Param("user") String user,
      @Param("book") String book,
      @Param("chapter") String chapter,
      @Param("type") String type,
      @Param("limit") int limit);

  List<LookupItem> vocabulary(
      @Param("user") String user, @Param("offset") int offset, @Param("limit") int limit);

  long vocabularyCount(String user);

  int insert(LookupItem value);

  int enrich(LookupItem value);

  int delete(@Param("user") String user, @Param("id") String id);

  Map<String, Object> summary(String user);

  List<Map<String, Object>> paragraphs(
      @Param("user") String user, @Param("book") String book, @Param("chapter") String chapter);
}
