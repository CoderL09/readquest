package com.readquest.service;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface DictionaryService {
  Map<String, Object> current(String user);

  Map<String, Object> install(String user, MultipartFile file);

  void delete(String user);

  Map<String, Object> lookup(String user, String word);
}
