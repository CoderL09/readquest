package com.readquest.mapper;

import com.readquest.pojo.*;

public interface SessionMapper {
  RefreshSession findById(String id);

  RefreshSession lockByHash(String hash);

  RefreshSession findByHash(String hash);

  RefreshSession activeFamily(
      @org.apache.ibatis.annotations.Param("user") String user,
      @org.apache.ibatis.annotations.Param("family") String family);

  int insert(RefreshSession session);

  int revoke(String id);

  int revokeFamily(
      @org.apache.ibatis.annotations.Param("user") String user,
      @org.apache.ibatis.annotations.Param("family") String family);
}
