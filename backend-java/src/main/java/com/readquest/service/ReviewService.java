package com.readquest.service;

import com.readquest.dto.ReviewRequests.*;
import com.readquest.pojo.LookupItem;
import java.util.*;

public interface ReviewService {
  Map<String, Object> card(String user, String id);

  Map<String, Object> source(String user, String id);

  Map<String, Object> mastery(String user, LookupItem item);

  Map<String, Map<String, Object>> masteryMany(String user, List<LookupItem> items);

  void syncWord(LookupItem item);

  Map<String, Object> createConcept(String user, Concept input);

  Map<String, Object> summary(String user);

  Map<String, Object> library(String user, String kind, int page, int size);

  Map<String, Object> active(String user);

  Map<String, Object> start(String user, int size);

  Map<String, Object> get(String user, String id);

  Map<String, Object> answer(String user, String session, String card, Answer input);

  Map<String, Object> advance(String user, String session, String event);

  Map<String, Object> complete(String user, String session);
}
