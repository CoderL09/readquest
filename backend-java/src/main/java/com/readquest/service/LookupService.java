package com.readquest.service;

import com.readquest.dto.LookupRequests.*;
import com.readquest.vo.PageResult;
import java.util.*;

public interface LookupService {
  Map<String, Object> get(String user, String id);

  Map<String, Object> create(String user, Create input);

  Map<String, Object> enrich(String user, String id, Enrichment input);

  void delete(String user, String id);

  List<Map<String, Object>> list(String user, String book, String chapter, String type, int limit);

  PageResult<Map<String, Object>> vocabulary(String user, int page, int size);

  Map<String, Object> summary(String user);

  List<Map<String, Object>> paragraphs(String user, String book, String chapter);
}
