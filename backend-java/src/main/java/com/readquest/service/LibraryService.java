package com.readquest.service;

import com.readquest.pojo.LibraryEntry;
import com.readquest.vo.PageResult;

public interface LibraryService {
  PageResult<com.readquest.vo.LibraryEntryView> list(String user, int page, int size);

  LibraryEntry put(String user, String book, String state);

  void delete(String user, String book);
}
