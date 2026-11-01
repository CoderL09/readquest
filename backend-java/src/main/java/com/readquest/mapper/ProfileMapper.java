package com.readquest.mapper;

import com.readquest.pojo.*;
import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface ProfileMapper {
  int ensure(String user);

  ReadingProfile profile(String user);

  int save(ReadingProfile profile);

  long metadataCount();

  int insertMetadata(BookMetadata metadata);

  List<BookMetadata> metadata();

  BookMetadata metadataById(String id);

  int insertCalibration(CalibrationSession session);

  CalibrationSession calibration(@Param("user") String user, @Param("id") String id);

  int saveCalibration(CalibrationSession session);

  List<CalibrationSample> samples(String session);

  int insertSample(CalibrationSample sample);

  int saveSample(CalibrationSample sample);

  List<ReadingProgress> recentProgress(String user);

  List<BookProgress> bookProgress(String user);

  Map<String, Object> reviewStats(String user);

  Map<String, Object> lookupStats(String user);

  List<LookupItem> flowItems(
      @Param("user") String user, @Param("book") String book, @Param("chapter") String chapter);
}
