package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.impl.HttpAiProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NativeToolProtocolTest {
  @Test
  void sendsToolsAndReplaysToolCallIdsWithoutFlatteningMessages() throws Exception {
    var json = new ObjectMapper();
    var seen = new AtomicReference<JsonNode>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          seen.set(json.readTree(exchange.getRequestBody()));
          byte[] body =
              "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"}}]}"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    var provider =
        new HttpAiProvider(
            json,
            "test-key",
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "test-model",
            3000);
    try {
      var messages =
          List.of(
              json.readTree(
                  "{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"getReviewWords\",\"arguments\":\"{}\"}}]}"),
              json.readTree("{\"role\":\"tool\",\"tool_call_id\":\"call-1\",\"content\":\"[]\"}"));
      assertEquals(
          "done",
          provider
              .turn(
                  messages,
                  List.of(
                      json.readTree(
                          "{\"type\":\"function\",\"function\":{\"name\":\"getReviewWords\"}}")))
              .path("content")
              .asText());
      assertTrue(seen.get().path("tools").isArray());
      assertEquals("call-1", seen.get().path("messages").path(1).path("tool_call_id").asText());
      assertEquals("disabled", seen.get().path("thinking").path("type").asText());
    } finally {
      provider.close();
      server.stop(0);
    }
  }
}
