package com.readquest.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface StudyAiService {
  JsonNode contextEmotion(String user, String book, String sentence);

  JsonNode intervention(String user, String surrounding, String chunk);

  Map<String, Object> quiz(String user, String chapter, boolean generate);
}
