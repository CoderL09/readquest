package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.imports.PublicUrls;
import com.readquest.service.RemoteFetcher;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Service;

@Service
public class SafeRemoteFetcher implements RemoteFetcher {
  private final com.readquest.imports.RemoteAddressResolver addresses;

  public SafeRemoteFetcher() {
    this(
        new com.readquest.imports.RemoteAddressResolver(
            new com.fasterxml.jackson.databind.ObjectMapper(), "system"));
  }

  @org.springframework.beans.factory.annotation.Autowired
  public SafeRemoteFetcher(com.readquest.imports.RemoteAddressResolver addresses) {
    this.addresses = addresses;
  }

  private final Semaphore concurrency = new Semaphore(8);
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }

  public Resource fetch(String value) {
    return fetch(value, Map.of());
  }

  @Override
  public Resource fetch(String value, Map<String, String> headers) {
    return fetch(value, headers, null);
  }

  public Resource image(String value, Set<String> hosts) {
    if (hosts == null || hosts.isEmpty())
      throw new ApiException(503, "Image download hosts are not configured");
    return fetch(value, Map.of(), Set.copyOf(hosts));
  }

  private Resource fetch(String value, Map<String, String> headers, Set<String> imageHosts) {
    URI initial = PublicUrls.normalize(value);
    if (!concurrency.tryAcquire())
      throw new ApiException(429, "RATE_LIMITED", "Too many remote requests");
    var active = new AtomicReference<HttpGet>();
    var future =
        workers.submit(() -> {
          try {
            return download(initial, active, headers, imageHosts);
          } finally {
            concurrency.release();
          }
        });
    try {
      return future.get(25, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Remote request interrupted");
    } catch (ExecutionException error) {
      if (error.getCause() instanceof ApiException api) throw api;
      throw new ApiException(503, "SOURCE_TEMPORARILY_UNAVAILABLE", "Source could not be fetched");
    } catch (TimeoutException error) {
      throw new ApiException(504, "SOURCE_TEMPORARILY_UNAVAILABLE", "Remote request timed out");
    } finally {
      HttpGet request = active.get();
      if (request != null) request.cancel();
      future.cancel(true);
    }
  }

  private Resource download(
      URI initial,
      AtomicReference<HttpGet> active,
      Map<String, String> headers,
      Set<String> imageHosts)
      throws Exception {
    URI current = initial;
    for (int redirects = 0; redirects <= 3; redirects++) {
      if (imageHosts != null
          && (!"https".equals(current.getScheme())
              || !imageHosts.contains(current.getHost().toLowerCase(Locale.ROOT))))
        throw new ApiException(502, "Untrusted image download URL");
      var resolver =
          new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException {
              return addresses.resolve(host);
            }

            public String resolveCanonicalHostname(String host) {
              return host;
            }
          };
      var manager =
          PoolingHttpClientConnectionManagerBuilder.create()
              .setDnsResolver(resolver)
              .setDefaultConnectionConfig(
                  ConnectionConfig.custom()
                      .setConnectTimeout(Timeout.ofSeconds(8))
                      .setSocketTimeout(Timeout.ofSeconds(12))
                      .build())
              .build();
      try (var client =
          HttpClients.custom()
              .setConnectionManager(manager)
              .disableRedirectHandling()
              .disableAutomaticRetries()
              .disableCookieManagement()
              .disableContentCompression()
              .setDefaultRequestConfig(
                  RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(12)).build())
              .build()) {
        var request = new HttpGet(current);
        active.set(request);
        request.setHeader("User-Agent", "ReadQuest/1.0 (user-requested import)");
        request.setHeader("Accept-Encoding", "identity");
        if (current.getHost().equals(initial.getHost())) headers.forEach(request::setHeader);
        URI requested = current;
        record Result(URI redirect, Resource resource) {}
        Result result =
            client.execute(
                request,
                response -> {
                  int status = response.getCode();
                  if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                    if (imageHosts != null)
                      throw new ApiException(502, "Image redirects are not permitted");
                    var location = response.getFirstHeader("Location");
                    if (location == null)
                      throw new ApiException(
                          422, "URL_REDIRECT_BLOCKED", "Redirect has no destination");
                    URI target =
                        PublicUrls.normalize(requested.resolve(location.getValue()).toString());
                    if (requested.getScheme().equals("https") && target.getScheme().equals("http"))
                      throw new ApiException(
                          422, "URL_REDIRECT_BLOCKED", "Insecure redirect blocked");
                    return new Result(target, null);
                  }
                  if (status == 401 || status == 403)
                    throw new ApiException(
                        422, "PAGE_REQUIRES_LOGIN", "Source requires login or permission");
                  if (status < 200 || status >= 300)
                    throw new ApiException(
                        503,
                        "SOURCE_TEMPORARILY_UNAVAILABLE",
                        "Source did not return a successful response");
                  var entity = response.getEntity();
                  if (entity == null)
                    throw new ApiException(
                        422, "UNSUPPORTED_CONTENT_TYPE", "Source has no content");
                  String type =
                      Objects.toString(entity.getContentType(), "application/octet-stream")
                          .split(";", 2)[0]
                          .strip()
                          .toLowerCase(Locale.ROOT);
                  if (imageHosts != null
                      ? !Set.of("image/png", "image/jpeg", "image/webp", "application/octet-stream")
                          .contains(type)
                      : !Set.of(
                              "text/html",
                              "application/xhtml+xml",
                              "text/plain",
                              "application/pdf",
                              "application/epub+zip",
                              "application/octet-stream",
                              "application/json")
                          .contains(type))
                    throw new ApiException(
                        422, "UNSUPPORTED_CONTENT_TYPE", "Unsupported source format");
                  int maximum =
                      imageHosts != null
                          ? 8 * 1024 * 1024
                          : type.contains("html")
                              ? 5 * 1024 * 1024
                              : type.equals("application/json")
                                  ? 4 * 1024 * 1024
                                  : 50 * 1024 * 1024;
                  if (entity.getContentLength() > maximum)
                    throw new ApiException(
                        413, "CONTENT_TOO_LARGE", "Remote content exceeds import limit");
                  String encoding =
                      Objects.toString(entity.getContentEncoding(), "")
                          .strip()
                          .toLowerCase(Locale.ROOT);
                  if (!Set.of("", "identity", "gzip").contains(encoding))
                    throw new ApiException(
                        422, "UNSUPPORTED_CONTENT_TYPE", "Unsupported source encoding");
                  byte[] bytes;
                  try (InputStream raw = entity.getContent();
                      InputStream stream =
                          encoding.equals("gzip") ? new GZIPInputStream(raw) : raw) {
                    bytes = stream.readNBytes(maximum + 1);
                  }
                  if (bytes.length > maximum)
                    throw new ApiException(
                        413, "CONTENT_TOO_LARGE", "Expanded source exceeds import limit");
                  String name =
                      requested.getPath().substring(requested.getPath().lastIndexOf('/') + 1);
                  var disposition = response.getFirstHeader("Content-Disposition");
                  if (disposition != null) {
                    var matcher =
                        java.util.regex.Pattern.compile(
                                "(?i)filename\\*?=(?:UTF-8''|\\\")?([^\\\";]+)")
                            .matcher(disposition.getValue());
                    if (matcher.find())
                      name =
                          URLDecoder.decode(
                              matcher.group(1).strip().replace("+", "%2B"), StandardCharsets.UTF_8);
                  }
                  name = name.replace('\\', '/');
                  name = name.substring(name.lastIndexOf('/') + 1);
                  if (name.length() > 255) name = name.substring(0, 255);
                  return new Result(null, new Resource(requested, type, name, bytes));
                });
        if (result.redirect() == null) return result.resource();
        current = result.redirect();
      }
    }
    throw new ApiException(422, "URL_REDIRECT_BLOCKED", "Too many source redirects");
  }
}
