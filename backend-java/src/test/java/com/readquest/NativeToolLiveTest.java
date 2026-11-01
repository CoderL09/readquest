package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.impl.HttpAiProvider;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AGENT_LIVE_ENABLED", matches = "true")
class NativeToolLiveTest {
  @Test
  void configuredProviderCanCompleteANativeToolRoundTrip() throws Exception {
    var json = new ObjectMapper();
    var provider =
        new HttpAiProvider(
            json,
            System.getenv("DEEPSEEK_API_KEY"),
            System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
            System.getenv().getOrDefault("DEEPSEEK_CHAT_MODEL", "deepseek-v4-flash"),
            30000);
    try {
      var tools =
          List.of(
              json.readTree(
                  "{\"type\":\"function\",\"function\":{\"name\":\"getReadingProgress\",\"description\":\"Read the current user's progress\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}}}"));
      var messages = new ArrayList<JsonNode>();
      messages.add(
          json.valueToTree(
              Map.of(
                  "role",
                  "user",
                  "content",
                  "Call getReadingProgress exactly once, then briefly summarize its result. Do not guess my progress.")));
      var first = provider.turn(messages, tools);
      assertTrue(first.path("tool_calls").isArray());
      assertEquals(1, first.path("tool_calls").size());
      assertEquals(
          "getReadingProgress",
          first.path("tool_calls").path(0).path("function").path("name").asText());
      messages.add(first);
      messages.add(
          json.valueToTree(
              Map.of(
                  "role",
                  "tool",
                  "tool_call_id",
                  first.path("tool_calls").path(0).path("id").asText(),
                  "content",
                  "{\"chapter\":2,\"completedParagraphs\":12}")));
      var last = provider.turn(messages, tools);
      assertFalse(last.path("content").asText().isBlank());
    } finally {
      provider.close();
    }
  }
}
