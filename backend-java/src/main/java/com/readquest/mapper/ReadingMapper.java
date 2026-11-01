package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface ReadingMapper {
  Chapter chapter(String id);

  User lockUser(String id);

  ReadingEvent event(@Param("user") String user, @Param("event") String event);

  int insertEvent(ReadingEvent event);

  int mergeProgress(ReadingEvent event);
}
