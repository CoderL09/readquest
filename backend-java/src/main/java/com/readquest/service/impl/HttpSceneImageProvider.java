package com.readquest.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.exception.ApiException;
import com.readquest.service.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HttpSceneImageProvider implements SceneImageProvider {
  private final ObjectMapper json;
  private final RemoteFetcher fetcher;
  private final String key, model, size;
  private final URI endpoint;
  private final Set<String> hosts;
  private final Semaphore capacity = new Semaphore(2);
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public HttpSceneImageProvider(
      ObjectMapper json,
      RemoteFetcher fetcher,
      @Value("${IMAGE_API_KEY:${SILICONFLOW_API_KEY:}}") String key,
      @Value("${IMAGE_GENERATION_URL:https://api.siliconflow.cn/v1/images/generations}")
          String endpoint,
      @Value("${IMAGE_MODEL:Kwai-Kolors/Kolors}") String model,
      @Value("${IMAGE_SIZE:1024x1024}") String size,
      @Value(
              "${IMAGE_DOWNLOAD_HOSTS:api.siliconflow.cn,s3.siliconflow.cn,sf-maas-api-prod.oss-cn-shanghai.aliyuncs.com,sf-maas-uat-prod.oss-cn-shanghai.aliyuncs.com}")
          String hosts) {
    this.json = json;
    this.fetcher = fetcher;
    this.key = key;
    this.model = model;
    this.size = size;
    this.endpoint = URI.create(endpoint);
    this.hosts =
        new HashSet<>(
            Arrays.stream(hosts.split(","))
                .map(String::strip)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .filter(h -> !h.isBlank())
                .toList());
    if (this.endpoint.getUserInfo() != null
        || !("https".equals(this.endpoint.getScheme())
            || ("http".equals(this.endpoint.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(this.endpoint.getHost()))))
      throw new IllegalArgumentException(
          "Image endpoint must use HTTPS, except localhost test endpoints");
  }

  @jakarta.annotation.PreDestroy
  public void close() {
    client.close();
  }

  public boolean configured() {
    return !key.isBlank();
  }

  public byte[] generate(String prompt) {
    if (!configured()) throw new ApiException(503, "Image generation is not configured");
    if (!capacity.tryAcquire()) throw new ApiException(429, "Image generation is busy");
    try {
      var request =
          HttpRequest.newBuilder(endpoint)
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + key)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of(
                              "model",
                              model,
                              "prompt",
                              prompt,
                              "negative_prompt",
                              "text, watermark, logo, UI, distorted anatomy, low quality",
                              "image_size",
                              size,
                              "batch_size",
                              1,
                              "num_inference_steps",
                              20,
                              "guidance_scale",
                              7.5))))
              .build();
      var pending =
          client.sendAsync(request, info -> new HttpAiProvider.BoundedBodySubscriber(256 * 1024));
      HttpResponse<byte[]> response;
      try {
        response = pending.get(21000, TimeUnit.MILLISECONDS);
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
      if (response.statusCode() != 200)
        throw new ApiException(502, "Image provider request failed");
      var url = json.readTree(response.body()).path("images").path(0).path("url");
      if (!url.isTextual() || url.asText().isBlank() || url.asText().length() > 4096)
        throw new ApiException(502, "Image provider returned invalid output");
      return fetcher.image(url.asText(), hosts).bytes();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(499, "Image generation was cancelled");
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(502, "Image provider is unavailable");
    } finally {
      capacity.release();
    }
  }
}
