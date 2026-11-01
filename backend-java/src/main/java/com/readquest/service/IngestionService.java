package com.readquest.service;

import com.readquest.pojo.*;
import org.springframework.web.multipart.MultipartFile;

public interface IngestionService {
  record Admission(Book book, BookImportJob job, boolean duplicate) {}

  Admission upload(String user, MultipartFile file, boolean rightsAttested);

  Admission upload(
      String user, MultipartFile file, boolean rightsAttested, String mode, int wordsPerQuest);

  BookImportJob get(String user, String jobId);

  BookImportJob retry(String user, String bookId);

  BookImportJob cancel(String user, String jobId);

  void processPending();

  void remove(String user, String bookId);

  org.springframework.core.io.Resource download(String user, String bookId);
}
