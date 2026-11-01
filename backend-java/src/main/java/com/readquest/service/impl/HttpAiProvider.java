package com.readquest.service.impl;

import com.fasterxml.jackson.databind.*;
import com.readquest.exception.ApiException;
import com.readquest.service.AiProvider;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HttpAiProvider implements AiProvider {
  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }

  private final ObjectMapper mapper;
  private final String key, model;
  private final URI endpoint;
  private final int timeout;
  private final Semaphore permits = new Semaphore(3, true);
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public HttpAiProvider(
      ObjectMapper mapper,
      @Value("${DEEPSEEK_API_KEY:}") String key,
      @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com/v1}") String base,
      @Value("${DEEPSEEK_CHAT_MODEL:deepseek-v4-flash}") String model,
      @Value("${DEEPSEEK_TIMEOUT_MS:12000}") int timeout) {
    this.mapper = mapper;
    this.key = key;
    this.model = model;
    this.timeout = Math.max(3000, Math.min(30000, timeout));
    endpoint = URI.create(base.replaceAll("/+$", "") + "/chat/completions");
    if (endpoint.getUserInfo() != null
        || !("https".equals(endpoint.getScheme())
            || ("http".equals(endpoint.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))))
      throw new IllegalArgumentException(
          "AI endpoint must use HTTPS, except localhost test endpoints");
  }

  public boolean configured() {
    return !key.isBlank();
  }

  public JsonNode turn(List<JsonNode> messages, List<JsonNode> tools) {
    if (!configured()) throw new ApiException(503, "AI provider is not configured");
    boolean acquired = false;
    try {
      if (!permits.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS))
        throw new ApiException(429, "AI provider concurrency limit reached");
      acquired = true;
      var payload =
          Map.of(
              "model",
              model,
              "max_tokens",
              2048,
              "thinking",
              Map.of("type", "disabled"),
              "messages",
              messages,
              "tools",
              tools,
              "tool_choice",
              "auto");
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofMillis(timeout))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
              .build();
      var pending = client.sendAsync(request, info -> new BoundedBodySubscriber(1024 * 1024));
      try {
        var response = pending.get(timeout + 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (response.statusCode() != 200)
          throw new ApiException(
              502,
              "AI_PROVIDER_HTTP_" + response.statusCode(),
              "AI tool request failed (HTTP " + response.statusCode() + ")");
        JsonNode message = mapper.readTree(response.body()).path("choices").path(0).path("message");
        if (!message.isObject() || message.toString().length() > 64000)
          throw new ApiException(502, "Invalid tool response");
        return message;
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(499, "AI request cancelled");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "AI tool provider unavailable");
    } finally {
      if (acquired) permits.release();
    }
  }

  private String complete(String system, String content, boolean json) {
    if (!configured()) throw new ApiException(503, "AI provider is not configured");
    boolean acquired = false;
    try {
      if (!permits.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS))
        throw new ApiException(429, "AI provider concurrency limit reached");
      acquired = true;
      Map<String, Object> payload = new HashMap<>();
      payload.put("model", model);
      payload.put("temperature", 0.2);
      payload.put("max_tokens", json ? 4096 : 2048);
      payload.put("thinking", Map.of("type", "disabled"));
      payload.put(
          "messages",
          List.of(
              Map.of("role", "system", "content", system),
              Map.of("role", "user", "content", content)));
      if (json) payload.put("response_format", Map.of("type", "json_object"));
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofMillis(timeout))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
              .build();
      var pending = client.sendAsync(request, info -> new BoundedBodySubscriber(1024 * 1024));
      HttpResponse<byte[]> response;
      try {
        response = pending.get(timeout + 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
      {
        byte[] bytes = response.body();
        if (response.statusCode() != 200 || bytes.length > 1024 * 1024)
          throw new ApiException(
              502,
              "AI_PROVIDER_HTTP_" + response.statusCode(),
              "AI provider request failed (HTTP " + response.statusCode() + ")");
        JsonNode choice = mapper.readTree(bytes).path("choices").path(0);
        if ("length".equals(choice.path("finish_reason").asText()))
          throw new ApiException(
              502,
              "AI_OUTPUT_TRUNCATED",
              "AI output exceeded its budget; please retry with a shorter passage");
        JsonNode result = choice.path("message").path("content");
        if (!result.isTextual() || result.asText().isBlank() || result.asText().length() > 64000)
          throw new ApiException(502, "AI provider returned invalid output");
        return result.asText();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(499, "AI request was cancelled");
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "AI provider is unavailable");
    } finally {
      if (acquired) permits.release();
    }
  }

  public String text(String system, String content) {
    return complete(system, content, false);
  }

  public void stream(String system, String content, java.util.function.Consumer<String> token) {
    if (!configured()) throw new ApiException(503, "AI provider is not configured");
    boolean acquired = false;
    try {
      if (!permits.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS))
        throw new ApiException(429, "AI provider concurrency limit reached");
      acquired = true;
      var payload =
          Map.of(
              "model",
              model,
              "temperature",
              0.2,
              "max_tokens",
              2048,
              "thinking",
              Map.of("type", "disabled"),
              "stream",
              true,
              "messages",
              List.of(
                  Map.of("role", "system", "content", system),
                  Map.of("role", "user", "content", content)));
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofMillis(timeout))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
              .build();
      var pending =
          client.sendAsync(
              request,
              info -> {
                if (info.statusCode() != 200)
                  throw new ApiException(502, "AI provider request failed");
                return new AiTokenSubscriber(mapper, token);
              });
      try {
        pending.get(timeout + 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(499, "AI request was cancelled");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(502, "AI stream is unavailable");
    } finally {
      if (acquired) permits.release();
    }
  }

  static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    private final java.util.concurrent.CompletableFuture<byte[]> body =
        new java.util.concurrent.CompletableFuture<>();
    private java.util.concurrent.Flow.Subscription subscription;

    BoundedBodySubscriber(int limit) {
      this.limit = limit;
    }

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return body;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
      if (subscription != null) {
        value.cancel();
        return;
      }
      subscription = value;
      value.request(1);
    }

    public void onNext(List<java.nio.ByteBuffer> buffers) {
      if (body.isDone()) return;
      for (var buffer : buffers) {
        if (buffer.remaining() > limit - output.size()) {
          subscription.cancel();
          body.completeExceptionally(new ApiException(502, "AI response exceeds size limit"));
          return;
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        output.writeBytes(bytes);
      }
      subscription.request(1);
    }

    public void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    public void onComplete() {
      body.complete(output.toByteArray());
    }
  }

  public JsonNode json(String system, String content) {
    try {
      JsonNode value = mapper.readTree(complete(system, content, true));
      if (!value.isObject()) throw new IllegalArgumentException();
      return value;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(502, "AI provider returned invalid JSON");
    }
  }
}
