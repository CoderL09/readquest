package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.impl.HttpAiProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpAiProviderTest {
  @Test
  void talksToOpenAiCompatibleEndpointWithoutExternalNetwork() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var json = new ObjectMapper();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          assertEquals(
              "Bearer test-provider-key", exchange.getRequestHeaders().getFirst("Authorization"));
          var body = json.readTree(exchange.getRequestBody());
          assertEquals("json_object", body.path("response_format").path("type").asText());
          assertEquals(4096, body.path("max_tokens").asInt());
          assertEquals("disabled", body.path("thinking").path("type").asText());
          byte[] response =
              "{\"choices\":[{\"message\":{\"content\":\"{\\\"sense_groups_text\\\":\\\"Hello / world.\\\"}\"}}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new HttpAiProvider(
              json,
              "test-provider-key",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              "test-model",
              3000);
      assertEquals(
          "Hello / world.",
          provider.json("system", "Hello world.").path("sense_groups_text").asText());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsCredentialBearingOrInsecureRemoteEndpoint() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new HttpAiProvider(
                new ObjectMapper(), "key", "http://remote.example/v1", "model", 3000));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new HttpAiProvider(
                new ObjectMapper(),
                "key",
                "https://user:password@remote.example/v1",
                "model",
                3000));
  }
}
