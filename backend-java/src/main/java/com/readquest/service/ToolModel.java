package com.readquest.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Native chat-completions tool calls, not JSON instructions masquerading as tools. */
public interface ToolModel {
  JsonNode turn(List<JsonNode> messages, List<JsonNode> tools);
}
