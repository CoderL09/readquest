package com.readquest.imports;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Public-only DNS pinning; optional authenticated HTTPS resolver for local Fake-IP proxies. */
@Component
public class RemoteAddressResolver {
  private record Entry(long expires, InetAddress[] addresses) {}

  private final String mode;
  private final ObjectMapper json;
  private final Map<String, Entry> cache = new LinkedHashMap<>();
  private HttpClient client;

  public RemoteAddressResolver(ObjectMapper json, @Value("${REMOTE_DNS_MODE:system}") String mode) {
    if (!Set.of("system", "doh").contains(mode))
      throw new IllegalArgumentException("REMOTE_DNS_MODE must be system or doh");
    this.json = json;
    this.mode = mode;
  }

  private synchronized HttpClient client() {
    if (client == null)
      client =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(3))
              .followRedirects(HttpClient.Redirect.NEVER)
              .build();
    return client;
  }

  @jakarta.annotation.PreDestroy
  public synchronized void close() {
    if (client != null) client.close();
  }

  public InetAddress[] resolve(String host) throws UnknownHostException {
    if (mode.equals("system")) return PublicUrls.resolve(host);
    if (!host.matches("[A-Za-z0-9.-]{1,253}"))
      throw new UnknownHostException("Invalid public hostname");
    if (host.matches("[0-9.]+"))
      return parsePublicAnswers(
          json.createArrayNode().add(json.createObjectNode().put("type", 1).put("data", host)));
    String name = host.toLowerCase(Locale.ROOT);
    synchronized (cache) {
      var existing = cache.get(name);
      if (existing != null && existing.expires() > System.currentTimeMillis())
        return existing.addresses().clone();
    }
    try {
      var request =
          HttpRequest.newBuilder(URI.create("https://dns.google/resolve?type=A&name=" + name))
              .timeout(Duration.ofSeconds(5))
              .header("Accept", "application/dns-json")
              .GET()
              .build();
      var pending = client().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
      try {
        var response = pending.get(6, java.util.concurrent.TimeUnit.SECONDS);
        try (var body = response.body()) {
          if (response.statusCode() != 200) throw new IllegalStateException();
          byte[] bytes = body.readNBytes(65537);
          if (bytes.length > 65536) throw new IllegalStateException();
          var answer = json.readTree(bytes);
          if (answer.path("Status").asInt(-1) != 0) throw new IllegalStateException();
          InetAddress[] addresses = parsePublicAnswers(answer.path("Answer"));
          synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getValue().expires() <= System.currentTimeMillis());
            if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
            cache.put(name, new Entry(System.currentTimeMillis() + 60000, addresses));
          }
          return addresses.clone();
        }
      } finally {
        if (!pending.isDone()) pending.cancel(true);
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new UnknownHostException("Public DNS request interrupted");
    } catch (Exception error) {
      throw new UnknownHostException("Public DNS resolution failed");
    }
  }

  public static InetAddress[] parsePublicAnswers(JsonNode records) throws UnknownHostException {
    var out = new LinkedHashSet<InetAddress>();
    if (!records.isArray()) throw new UnknownHostException("Missing DNS answers");
    for (var record : records) {
      if (record.path("type").asInt() != 1) continue;
      String value = record.path("data").asText();
      if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
        throw new UnknownHostException("DNS answer must be an IPv4 literal");
      var address = InetAddress.getByName(value);
      if (PublicUrls.blocked(address)) throw new UnknownHostException("DNS answer must be public");
      out.add(address);
    }
    if (out.isEmpty()) throw new UnknownHostException("No public IPv4 answer");
    return out.toArray(InetAddress[]::new);
  }
}
