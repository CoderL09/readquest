package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;

/** Bounded OpenAI-compatible SSE decoder; never buffers an unbounded provider line. */
final class AiTokenSubscriber implements HttpResponse.BodySubscriber<Void> {
  private final ObjectMapper json;
  private final Consumer<String> token;
  private final CompletableFuture<Void> body = new CompletableFuture<>();
  private final ByteArrayOutputStream line = new ByteArrayOutputStream();
  private final StringBuilder data = new StringBuilder();
  private Subscription subscription;
  private boolean done;
  private int bytes, characters;

  AiTokenSubscriber(ObjectMapper json, Consumer<String> token) {
    this.json = json;
    this.token = token;
  }

  public CompletionStage<Void> getBody() {
    return body;
  }

  public void onSubscribe(Subscription value) {
    subscription = value;
    value.request(1);
  }

  public void onNext(List<ByteBuffer> buffers) {
    if (body.isDone()) return;
    try {
      for (var buffer : buffers)
        while (buffer.hasRemaining()) {
          if (++bytes > 2 * 1024 * 1024)
            throw new ApiException(502, "AI stream exceeds size limit");
          byte value = buffer.get();
          if (value == '\n') {
            readLine();
            line.reset();
          } else {
            if (line.size() >= 65536)
              throw new ApiException(502, "AI stream line exceeds size limit");
            line.write(value);
          }
        }
      subscription.request(1);
    } catch (Throwable error) {
      subscription.cancel();
      body.completeExceptionally(error);
    }
  }

  private void readLine() throws Exception {
    String value = line.toString(StandardCharsets.UTF_8);
    if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
    if (value.isEmpty()) {
      event();
      return;
    }
    if (value.startsWith("data:")) {
      String part = value.substring(5);
      if (part.startsWith(" ")) part = part.substring(1);
      if (data.length() + part.length() > 65536)
        throw new ApiException(502, "AI stream event exceeds size limit");
      if (!data.isEmpty()) data.append('\n');
      data.append(part);
    }
  }

  private void event() throws Exception {
    if (data.isEmpty()) return;
    String value = data.toString();
    data.setLength(0);
    if (value.equals("[DONE]")) {
      done = true;
      return;
    }
    if (done) return;
    var root = json.readTree(value);
    if (root.has("error")) throw new ApiException(502, "AI provider stream failed");
    var content = root.path("choices").path(0).path("delta").path("content");
    if (content.isMissingNode() || content.isNull()) return;
    if (!content.isTextual()) throw new ApiException(502, "AI provider returned invalid token");
    String delta = content.asText();
    characters += delta.length();
    if (characters > 64000) throw new ApiException(502, "AI answer exceeds size limit");
    if (!delta.isEmpty()) token.accept(delta);
  }

  public void onError(Throwable error) {
    body.completeExceptionally(error);
  }

  public void onComplete() {
    try {
      if (line.size() > 0) {
        readLine();
        line.reset();
      }
      event();
      if (!done || characters == 0) throw new ApiException(502, "AI stream was incomplete");
      body.complete(null);
    } catch (Throwable error) {
      body.completeExceptionally(error);
    }
  }
}
