package com.readquest.service.impl;

import com.fasterxml.jackson.databind.*;
import com.readquest.exception.ApiException;
import com.readquest.service.EmbeddingProvider;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HttpEmbeddingProvider implements EmbeddingProvider {
  private final ObjectMapper json;
  private final String key, model;
  private final URI endpoint;
  private final Semaphore permits = new Semaphore(2, true);
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public HttpEmbeddingProvider(
      ObjectMapper json,
      @Value("${EMBEDDING_API_KEY:${SILICONFLOW_API_KEY:}}") String key,
      @Value("${EMBEDDING_BASE_URL:https://api.siliconflow.cn/v1}") String base,
      @Value("${MODULE1_EMBEDDING_MODEL:${EMBEDDING_MODEL:BAAI/bge-m3}}") String model) {
    this.json = json;
    this.key = key;
    this.model = model;
    endpoint = URI.create(base.replaceAll("/+$", "") + "/embeddings");
    if (endpoint.getUserInfo() != null
        || !("https".equals(endpoint.getScheme())
            || ("http".equals(endpoint.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))))
      throw new IllegalArgumentException(
          "Embedding endpoint must use HTTPS, except localhost test endpoints");
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }

  public boolean configured() {
    return !key.isBlank();
  }

  public String modelKey() {
    return endpoint + "#" + model;
  }

  public List<double[]> embed(List<String> texts) {
    if (!configured()) throw new ApiException(503, "Embedding provider is not configured");
    if (texts.isEmpty()
        || texts.size() > 16
        || texts.stream().anyMatch(t -> t.isBlank() || t.length() > 5000))
      throw new IllegalArgumentException("Invalid embedding batch");
    boolean acquired = false;
    try {
      if (!permits.tryAcquire(3, TimeUnit.SECONDS))
        throw new ApiException(429, "Embedding provider concurrency limit reached");
      acquired = true;
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(12))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of("model", model, "input", texts, "encoding_format", "float"))))
              .build();
      var pending =
          client.sendAsync(
              request, info -> new HttpAiProvider.BoundedBodySubscriber(2 * 1024 * 1024));
      HttpResponse<byte[]> response;
      try {
        response = pending.get(13000, TimeUnit.MILLISECONDS);
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
      if (response.statusCode() != 200)
        throw new ApiException(502, "Embedding provider request failed");
      JsonNode data = json.readTree(response.body()).path("data");
      if (!data.isArray() || data.size() != texts.size()) throw new IllegalArgumentException();
      double[][] vectors = new double[texts.size()][];
      int dimensions = -1;
      for (JsonNode entry : data) {
        JsonNode indexNode = entry.path("index"), values = entry.path("embedding");
        if (!indexNode.isIntegralNumber() || !indexNode.canConvertToInt())
          throw new IllegalArgumentException();
        int index = indexNode.intValue();
        if (index < 0
            || index >= vectors.length
            || vectors[index] != null
            || !values.isArray()
            || values.size() < 2
            || values.size() > 4096
            || (dimensions != -1 && dimensions != values.size()))
          throw new IllegalArgumentException();
        dimensions = values.size();
        double[] vector = new double[dimensions];
        double norm = 0;
        for (int i = 0; i < dimensions; i++) {
          if (!values.get(i).isNumber()) throw new IllegalArgumentException();
          vector[i] = values.get(i).doubleValue();
          if (!Double.isFinite(vector[i])) throw new IllegalArgumentException();
          norm += vector[i] * vector[i];
        }
        if (!Double.isFinite(norm) || norm <= 0) throw new IllegalArgumentException();
        vectors[index] = vector;
      }
      return Arrays.asList(vectors);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(499, "Embedding request was cancelled");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(502, "Embedding provider returned invalid or unavailable output");
    } finally {
      if (acquired) permits.release();
    }
  }
}
