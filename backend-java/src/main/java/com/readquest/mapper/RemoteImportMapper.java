package com.readquest.mapper;

import com.readquest.pojo.RemoteImportJob;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RemoteImportMapper {
  RemoteImportJob find(@Param("user") String user, @Param("id") String id);

  RemoteImportJob byUrl(@Param("user") String user, @Param("hash") String hash);

  List<RemoteImportJob> pending();

  int insert(RemoteImportJob job);

  int retry(@Param("user") String user, @Param("id") String id);

  int claim(@Param("id") String id, @Param("run") String run);

  int ingest(
      @Param("id") String id,
      @Param("run") String run,
      @Param("book") String book,
      @Param("fileJob") String fileJob,
      @Param("kind") String kind);

  int fail(@Param("id") String id, @Param("run") String run, @Param("code") String code);

  int terminal(@Param("id") String id, @Param("status") String status, @Param("code") String code);

  int source(
      @Param("book") String book, @Param("title") String title, @Param("metadata") String metadata);
}
