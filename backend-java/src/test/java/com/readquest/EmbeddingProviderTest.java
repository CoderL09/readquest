package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.service.impl.HttpEmbeddingProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EmbeddingProviderTest {
  @Test
  void validatesIndicesDimensionsAndFiniteNonzeroVectors() throws Exception {
    var reply =
        new AtomicReference<>(
            "{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/embeddings",
        exchange -> {
          assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
          assertEquals(
              2, new ObjectMapper().readTree(exchange.getRequestBody()).path("input").size());
          byte[] response = reply.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    var provider =
        new HttpEmbeddingProvider(
            new ObjectMapper(),
            "test-key",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
            "test-model");
    try {
      assertArrayEquals(new double[] {1, 0}, provider.embed(List.of("a", "b")).getFirst());
      for (String invalid :
          List.of(
              "{\"data\":[{\"index\":0,\"embedding\":[1,0]},{\"index\":0,\"embedding\":[0,1]}]}",
              "{\"data\":[{\"index\":0,\"embedding\":[0,0]},{\"index\":1,\"embedding\":[0,1]}]}",
              "{\"data\":[{\"index\":0,\"embedding\":[1,0]},{\"index\":1,\"embedding\":[0,1,2]}]}",
              "{\"data\":[{\"index\":0,\"embedding\":[1e300,0]},{\"index\":1,\"embedding\":[0,1]}]}")) {
        reply.set(invalid);
        assertThrows(ApiException.class, () -> provider.embed(List.of("a", "b")));
      }
    } finally {
      provider.close();
      server.stop(0);
    }
  }
}
