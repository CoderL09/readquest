package com.readquest.service;

import com.readquest.dto.Requests.Progress;
import com.readquest.pojo.ReadingProgress;

public interface ProgressService {
  ReadingProgress get(String user, String book, String chapter);

  ReadingProgress put(String user, String book, String chapter, Progress in);
}
