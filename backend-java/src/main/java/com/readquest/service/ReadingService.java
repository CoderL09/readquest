package com.readquest.service;

import com.readquest.dto.ReadingEventInput;
import com.readquest.pojo.*;
import java.util.Map;

public interface ReadingService {
  Map<String, Object> structure(String user, String chapter);

  Map<String, Object> sentences(String user, String chapter);

  ReadingProgress record(String user, String book, String chapter, ReadingEventInput input);
}
