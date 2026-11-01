package com.readquest.mapper;

import com.readquest.pojo.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IngestionMapper {
  int metadata(
      @org.apache.ibatis.annotations.Param("book") String book,
      @org.apache.ibatis.annotations.Param("title") String title,
      @org.apache.ibatis.annotations.Param("author") String author,
      @org.apache.ibatis.annotations.Param("cover") String cover,
      @org.apache.ibatis.annotations.Param("metadata") String metadata);

  User lockActiveUser(String id);

  Book lockBook(String id);

  Book findDuplicate(@Param("owner") String owner, @Param("hash") String hash);

  int insertBook(Book book);

  int insertChapter(Chapter chapter);

  int insertJob(BookImportJob job);

  BookImportJob findJob(@Param("user") String user, @Param("id") String id);

  BookImportJob latestJob(@Param("user") String user, @Param("book") String book);

  List<BookImportJob> pending();

  List<BookImportJob> exhausted();

  int claim(@Param("id") String id, @Param("run") String run);

  int renew(@Param("id") String id, @Param("run") String run);

  int checkpoint(
      @Param("id") String id,
      @Param("run") String run,
      @Param("phase") String phase,
      @Param("progress") int progress);

  int cancelJobs(@Param("book") String book);

  int resetBook(@Param("book") String book);

  int cancelBook(@Param("book") String book);

  int progressBook(@Param("book") String book, @Param("progress") int progress);

  BookImportJob lockClaim(@Param("id") String id, @Param("run") String run);

  int finishJob(@Param("id") String id, @Param("run") String run);

  int failJob(@Param("id") String id, @Param("run") String run, @Param("message") String message);

  int completeBook(
      @Param("id") String id, @Param("chapters") int chapters, @Param("words") long words);

  int failBook(@Param("id") String id, @Param("message") String message);

  int removeBook(@Param("id") String id, @Param("owner") String owner);
}
