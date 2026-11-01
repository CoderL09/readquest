package com.readquest.service.impl;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.ToolModel;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Separate provider bulkhead for bounded agent tasks. */
@Service
public class HttpToolModel implements ToolModel {
  private final HttpAiProvider provider;

  public HttpToolModel(ObjectMapper json, Environment env) {
    provider =
        new HttpAiProvider(
            json,
            env.getProperty("DEEPSEEK_API_KEY", ""),
            env.getProperty("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
            env.getProperty("DEEPSEEK_CHAT_MODEL", "deepseek-v4-flash"),
            env.getProperty("DEEPSEEK_TIMEOUT_MS", Integer.class, 12000));
  }

  public JsonNode turn(List<JsonNode> messages, List<JsonNode> tools) {
    return provider.turn(messages, tools);
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    provider.close();
  }
}
