package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.imports.RemoteAddressResolver;
import com.readquest.service.impl.SafeRemoteFetcher;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "LIVE_IMAGE_REPLAY_FILE", matches = ".+")
class LiveImageDownloadReplayTest {
  @Test
  void publicDnsAndBoundedDownloadWorkWithoutGeneratingAnotherImage() throws Exception {
    var resolver = new RemoteAddressResolver(new ObjectMapper(), "doh");
    var fetcher = new SafeRemoteFetcher(resolver);
    try {
      String url = Files.readString(Path.of(System.getenv("LIVE_IMAGE_REPLAY_FILE")));
      var resource = fetcher.image(url, Set.of("s3.siliconflow.cn"));
      assertTrue(resource.bytes().length > 1000);
      try (var input = new java.io.ByteArrayInputStream(resource.bytes())) {
        assertNotNull(javax.imageio.ImageIO.read(input));
      }
      Files.write(Path.of("target/live-check/download-replay.png"), resource.bytes());
    } finally {
      fetcher.close();
      resolver.close();
    }
  }
}
