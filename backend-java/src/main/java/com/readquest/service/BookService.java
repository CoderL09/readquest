package com.readquest.service;

import com.readquest.pojo.*;
import com.readquest.vo.PageResult;
import java.util.List;

public interface BookService {
  PageResult<Book> list(String userId, int page, int size);

  PageResult<Book> uploads(String userId, int page, int size);

  Book get(String userId, String id);

  java.util.Map<String, Book> getMany(String userId, java.util.Collection<String> ids);

  Book requireReadable(String userId, String id);

  List<Chapter> chapters(String userId, String bookId);

  Chapter chapter(String userId, String bookId, String id);
}
