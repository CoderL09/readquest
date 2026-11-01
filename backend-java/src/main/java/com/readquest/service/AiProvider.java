package com.readquest.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiProvider {
  boolean configured();

  JsonNode json(String system, String content);

  String text(String system, String content);

  default void stream(String system, String content, java.util.function.Consumer<String> token) {
    token.accept(text(system, content));
  }
}
