package com.readquest.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import org.junit.jupiter.api.Test;

class AiTokenSubscriberTest {
  @Test
  void decodesUnicodeAcrossBuffersAndRequiresCompletionMarker() throws Exception {
    List<String> tokens = new ArrayList<>();
    var subscriber = new AiTokenSubscriber(new ObjectMapper(), tokens::add);
    subscriber.onSubscribe(
        new Subscription() {
          public void request(long count) {}

          public void cancel() {}
        });
    String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\r\n\r\ndata: [DONE]\n\n";
    for (byte value : sse.getBytes(StandardCharsets.UTF_8))
      subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {value})));
    subscriber.onComplete();
    subscriber.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS);
    assertEquals(List.of("你好"), tokens);
    var incomplete = new AiTokenSubscriber(new ObjectMapper(), tokens::add);
    incomplete.onSubscribe(
        new Subscription() {
          public void request(long count) {}

          public void cancel() {}
        });
    incomplete.onNext(
        List.of(
            ByteBuffer.wrap(
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                    .getBytes(StandardCharsets.UTF_8))));
    incomplete.onComplete();
    assertThrows(
        ExecutionException.class,
        () -> incomplete.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS));
  }

  @Test
  void rejectsUnboundedLinesAndCancelsUpstream() {
    boolean[] cancelled = {false};
    var subscriber = new AiTokenSubscriber(new ObjectMapper(), text -> {});
    subscriber.onSubscribe(
        new Subscription() {
          public void request(long count) {}

          public void cancel() {
            cancelled[0] = true;
          }
        });
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65537])));
    assertTrue(cancelled[0]);
    assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
  }
}
