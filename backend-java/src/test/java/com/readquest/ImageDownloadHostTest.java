package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.service.*;
import com.readquest.service.impl.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ImageDownloadHostTest {
  @Test
  void defaultConfigurationAllowsObservedProviderHostButNotLookalikes() throws Exception {
    check(null, true);
  }

  @Test
  void explicitHostOverrideRemainsAuthoritative() throws Exception {
    check("custom.example", false);
  }

  private void check(String override, boolean includesProvider) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/images",
        exchange -> {
          byte[] bytes =
              "{\"images\":[{\"url\":\"https://s3.siliconflow.cn/test-image\"}]}"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var fetcher = mock(RemoteFetcher.class);
    var observed = new AtomicReference<Set<String>>();
    when(fetcher.image(anyString(), anySet()))
        .thenAnswer(call -> {
          observed.set(Set.copyOf(call.getArgument(1)));
          return new RemoteFetcher.Resource(
              URI.create(call.getArgument(0)), "image/png", "fixture.png", new byte[] {1, 2, 3});
        });
    var runner =
        new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RemoteFetcher.class, () -> fetcher)
            .withUserConfiguration(HttpSceneImageProvider.class)
            .withPropertyValues(
                "IMAGE_API_KEY=fixture",
                "IMAGE_GENERATION_URL=http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/images");
    if (override != null) runner = runner.withPropertyValues("IMAGE_DOWNLOAD_HOSTS=" + override);
    try {
      runner.run(context -> {
        assertArrayEquals(
            new byte[] {1, 2, 3},
            context.getBean(HttpSceneImageProvider.class).generate("A fixture"));
        assertEquals(includesProvider, observed.get().contains("s3.siliconflow.cn"));
        var safe = new SafeRemoteFetcher();
        try {
          var error =
              assertThrows(
                  ApiException.class,
                  () ->
                      safe.image(
                          "https://s3.siliconflow.cn.evil.invalid/image.png", observed.get()));
          assertEquals(502, error.status());
        } finally {
          safe.close();
        }
      });
    } finally {
      server.stop(0);
    }
  }
}
