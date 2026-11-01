package com.readquest.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ReadingAiService {
  SseEmitter article(String user, String text);

  Map<String, Object> chunks(String user, String text, String mode);

  Map<String, Object> sentenceChunks(String user, String book, String chapter, String sentence);

  JsonNode explain(String user, String book, String text, String action, String context);

  Map<String, Object> chat(String user, String book, String question, String chapter);
}
