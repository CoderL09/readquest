package com.readquest.mapper;

import com.readquest.pojo.*;
import org.apache.ibatis.annotations.Param;

public interface StudyAiMapper {
  Chapter lockChapter(String chapter);

  ChapterQuiz quiz(String chapter);

  int claim(ChapterQuiz quiz);

  int publish(
      @Param("chapter") String chapter,
      @Param("request") String request,
      @Param("questions") String questions);

  int fail(@Param("chapter") String chapter, @Param("request") String request);
}
