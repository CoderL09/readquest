package com.readquest.service;

import com.readquest.dto.Requests.*;
import com.readquest.vo.BookNoteView;
import com.readquest.vo.PageResult;

public interface NoteService {
  PageResult<BookNoteView> list(String user, int page, int size);

  BookNoteView get(String user, String id);

  BookNoteView create(String user, CreateNote input);

  BookNoteView update(String user, String id, UpdateNote input);

  void delete(String user, String id);
}
