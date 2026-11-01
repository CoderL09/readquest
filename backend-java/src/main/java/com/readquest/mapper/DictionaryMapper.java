package com.readquest.mapper;

import com.readquest.pojo.UserDictionary;

public interface DictionaryMapper {
  UserDictionary find(String user);

  int insert(UserDictionary dictionary);

  int delete(String user);
}
