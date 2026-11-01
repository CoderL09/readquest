package com.readquest.mapper;

import com.readquest.pojo.*;

public interface UserMapper {
  User findById(String id);

  User findByEmail(String email);

  int insert(User user);

  int updateAvatar(
      @org.apache.ibatis.annotations.Param("user") String user,
      @org.apache.ibatis.annotations.Param("url") String url);
}
