package com.readquest.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.readquest.catalog.CatalogCards;
import com.readquest.exception.ApiException;
import com.readquest.mapper.CatalogMapper;
import com.readquest.pojo.CatalogSnapshot;
import com.readquest.service.*;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("unchecked")
public class CatalogServiceImpl implements CatalogService {
  private final RemoteFetcher remote;
  private final CatalogMapper mapper;
  private final RemoteImportService imports;
  private final MembershipService limits;
  private final ObjectMapper json;
  private final String googleKey;
  private final List<Map<String, Object>> categories, externalSources;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore providerSlots = new Semaphore(6);
  private final Object openGate = new Object();
  private long nextOpenRequest;

  private final com.readquest.infrastructure.CatalogResultCache cache;

  public CatalogServiceImpl(
      RemoteFetcher remote,
      CatalogMapper mapper,
      RemoteImportService imports,
      MembershipService limits,
      ObjectMapper json,
      com.readquest.infrastructure.CatalogResultCache cache,
      @Value("${GOOGLE_BOOK_API:}") String key)
      throws Exception {
    this.remote = remote;
    this.mapper = mapper;
    this.imports = imports;
    this.limits = limits;
    this.json = json;
    this.cache = cache;
    googleKey = key.strip();
    try (var input = new ClassPathResource("data/catalog-categories.json").getInputStream()) {
      categories = json.readValue(input, new TypeReference<>() {});
    }
    try (var input = new ClassPathResource("data/catalog-source-links.json").getInputStream()) {
      externalSources = json.readValue(input, new TypeReference<>() {});
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }

  private static Map<String, Object> map(Object... values) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) result.put(values[i].toString(), values[i + 1]);
    return result;
  }

  public List<Map<String, Object>> categories() {
    return categories;
  }

  public List<Map<String, Object>> sources() {
    var result = new ArrayList<Map<String, Object>>();
    result.add(
        map(
            "id",
            "googlebooks",
            "name",
            "Google Books",
            "homepage",
            "https://books.google.com/",
            "mode",
            "api",
            "enabled",
            !googleKey.isBlank(),
            "configured",
            !googleKey.isBlank(),
            "healthStatus",
            googleKey.isBlank() ? "disabled" : "healthy",
            "capabilities",
            List.of("search", "preview", "download"),
            "lastSuccessAt",
            null,
            "lastErrorCode",
            googleKey.isBlank() ? "PROVIDER_NOT_CONFIGURED" : null,
            "notice",
            "仅把已验证为完整公版、且 API 提供实际文件链接的书标记为可下载。"));
    result.add(
        map(
            "id",
            "openlibrary",
            "name",
            "Open Library",
            "homepage",
            "https://openlibrary.org/",
            "mode",
            "api",
            "enabled",
            true,
            "configured",
            true,
            "healthStatus",
            "healthy",
            "capabilities",
            List.of("search", "trending", "preview", "external-open"),
            "lastSuccessAt",
            null,
            "lastErrorCode",
            null,
            "notice",
            "提供检索、热门榜和跳转预览；不将借阅或全文标记为直接下载。"));
    result.addAll(externalSources);
    return result;
  }

  private void rate(String identity) {
    if (!limits.rateLimit("catalog", identity, 60, 60)
        || !limits.rateLimit("catalog-global", "all", 200, 60))
      throw new ApiException(429, "RATE_LIMITED", "Catalog request limit reached");
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private JsonNode providerJson(String url, boolean google) {
    if (!providerSlots.tryAcquire())
      throw new ApiException(503, "PROVIDER_RATE_LIMITED", "Catalog source is busy");
    try {
      if (!google)
        synchronized (openGate) {
          long pause = nextOpenRequest - System.currentTimeMillis();
          if (pause > 0) Thread.sleep(Math.min(1000, pause));
          nextOpenRequest = System.currentTimeMillis() + 1000;
        }
      var value =
          remote.fetch(
              url,
              google
                  ? Map.of("x-goog-api-key", googleKey, "Accept", "application/json")
                  : Map.of("Accept", "application/json"));
      if (value == null
          || !URI.create(url).getHost().equals(value.url().getHost())
          || value.bytes().length > 4 * 1024 * 1024)
        throw new ApiException(502, "INVALID_PROVIDER_RESPONSE", "Invalid catalog source response");
      return json.readTree(value.bytes());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "PROVIDER_UNAVAILABLE", "Catalog request interrupted");
    } catch (java.io.IOException error) {
      throw new ApiException(
          502, "INVALID_PROVIDER_RESPONSE", "Catalog source returned invalid JSON");
    } finally {
      providerSlots.release();
    }
  }

  private record Result(
      String provider, List<Map<String, Object>> items, int next, String state, String error) {}

  private Result providerSearch(
      String provider, String query, String category, int offset, int count) {
    if (provider.equals("googlebooks") && googleKey.isBlank())
      return new Result(provider, List.of(), -1, "disabled", "PROVIDER_NOT_CONFIGURED");
    if (offset < 0 || count == 0) return new Result(provider, List.of(), offset, "ok", null);
    try {
      String q = query + (category == null ? "" : " subject:" + category);
      boolean google = provider.equals("googlebooks");
      String url =
          google
              ? "https://www.googleapis.com/books/v1/volumes?q="
                  + encode(q)
                  + "&printType=books&projection=full&startIndex="
                  + offset
                  + "&maxResults="
                  + count
              : "https://openlibrary.org/search.json?q="
                  + encode(q)
                  + "&fields="
                  + encode(
                      "key,title,author_name,cover_i,first_publish_year,isbn,language,subject,ratings_average,ratings_count,has_fulltext,public_scan_b,ebook_access")
                  + "&offset="
                  + offset
                  + "&limit="
                  + count;
      var response = providerJson(url, google);
      JsonNode rows = response.path(google ? "items" : "docs");
      if (rows.isMissingNode() && google) rows = json.createArrayNode();
      if (!rows.isArray() || rows.size() > 100)
        throw new ApiException(502, "INVALID_PROVIDER_RESPONSE", "Invalid catalog rows");
      var cards = new ArrayList<Map<String, Object>>();
      for (var row : rows)
        cards.add(google ? CatalogCards.google(row) : CatalogCards.openLibrary(row));
      long total =
          google
              ? response.path("totalItems").asLong(cards.size())
              : response.path("numFound").asLong(response.path("num_found").asLong(cards.size()));
      return new Result(
          provider,
          cards,
          !cards.isEmpty() && offset + cards.size() < total ? offset + count : -1,
          "ok",
          null);
    } catch (RuntimeException error) {
      return new Result(
          provider,
          List.of(),
          offset,
          "failed",
          error instanceof ApiException api ? api.code() : "PROVIDER_UNAVAILABLE");
    }
  }

  private Map<String, Integer> cursor(String cursor, String fingerprint) {
    if (cursor == null || cursor.isBlank())
      return new LinkedHashMap<>(Map.of("googlebooks", 0, "openlibrary", 0));
    try {
      if (cursor.length() > 1024 || !cursor.matches("[A-Za-z0-9_-]+"))
        throw new IllegalArgumentException();
      var value = json.readTree(Base64.getUrlDecoder().decode(cursor));
      if (value.path("v").asInt() != 1
          || !fingerprint.equals(value.path("q").asText())
          || !value.path("o").isObject()
          || value.path("o").size() > 2) throw new IllegalArgumentException();
      var offsets = new LinkedHashMap<String, Integer>();
      var fields = value.path("o").fields();
      while (fields.hasNext()) {
        var field = fields.next();
        if (!Set.of("googlebooks", "openlibrary").contains(field.getKey())
            || !field.getValue().isIntegralNumber()
            || !field.getValue().canConvertToInt()
            || field.getValue().asInt() < -1
            || field.getValue().asInt() > 1000000) throw new IllegalArgumentException();
        offsets.put(field.getKey(), field.getValue().asInt());
      }
      return offsets;
    } catch (Exception error) {
      throw new ApiException(422, "INVALID_CURSOR", "Catalog cursor is invalid for this query");
    }
  }

  private void category(String category) {
    if (category != null
        && categories.stream().noneMatch(value -> category.equals(value.get("id"))))
      throw new ApiException(422, "Invalid catalog category");
  }

  public Map<String, Object> search(
      String identity, String query, String category, String provider, String cursor, int count) {
    rate(identity);
    category(category);
    if (query == null
        || query.isBlank()
        || query.length() > 200
        || !Set.of("combined", "googlebooks", "openlibrary").contains(provider)
        || count < 1
        || count > 40) throw new ApiException(422, "Invalid catalog search");
    query = query.strip().replaceAll("\\s+", " ");
    String fingerprint =
        CatalogCards.hash(query + "\0" + Objects.toString(category, "") + "\0" + provider);
    var offsets = cursor(cursor, fingerprint);
    String cacheKey = fingerprint + ":" + Objects.toString(cursor, "") + ":" + count;
    String normalizedQuery = query;
    return cache.getOrLoad(
        cacheKey,
        () -> searchProviders(normalizedQuery, category, provider, count, fingerprint, offsets));
  }

  private Map<String, Object> searchProviders(
      String query,
      String category,
      String provider,
      int count,
      String fingerprint,
      Map<String, Integer> offsets) {
    var selected =
        provider.equals("combined") ? List.of("googlebooks", "openlibrary") : List.of(provider);
    long enabled =
        selected.stream()
            .filter(id -> !id.equals("googlebooks") || !googleKey.isBlank())
            .filter(id -> offsets.getOrDefault(id, 0) >= 0)
            .count();
    var tasks = new ArrayList<Future<Result>>();
    int remaining = count;
    long left = enabled;
    String searchQuery = query;
    for (String id : selected) {
      boolean active =
          (!id.equals("googlebooks") || !googleKey.isBlank()) && offsets.getOrDefault(id, 0) >= 0;
      int allocated = active && left > 0 ? (int) Math.ceil((double) remaining / left) : 0;
      if (active) {
        remaining -= allocated;
        left--;
      }
      int size = allocated;
      tasks.add(
          workers.submit(
              () -> providerSearch(id, searchQuery, category, offsets.getOrDefault(id, 0), size)));
    }
    var responses = new ArrayList<Result>();
    try {
      for (var task : tasks) responses.add(task.get(30, TimeUnit.SECONDS));
    } catch (Exception error) {
      tasks.forEach(task -> task.cancel(true));
      throw new ApiException(503, "SOURCE_TEMPORARILY_UNAVAILABLE", "Catalog source unavailable");
    }
    if (responses.stream().noneMatch(value -> value.state().equals("ok")))
      throw new ApiException(
          503, "SOURCE_TEMPORARILY_UNAVAILABLE", "Selected catalog sources are unavailable");
    var cards = new LinkedHashMap<String, Map<String, Object>>();
    var statuses = new ArrayList<Map<String, Object>>();
    var next = new LinkedHashMap<String, Integer>();
    for (var result : responses) {
      statuses.add(
          map("id", result.provider(), "status", result.state(), "errorCode", result.error()));
      next.put(result.provider(), result.next());
      for (var card : result.items())
        cards.merge(card.get("canonicalKey").toString(), card, this::merge);
    }
    String nextCursor = null;
    if (next.values().stream().anyMatch(value -> value >= 0))
      try {
        nextCursor =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.writeValueAsBytes(map("v", 1, "q", fingerprint, "o", next)));
      } catch (Exception error) {
        throw new IllegalStateException(error);
      }
    var result =
        map(
            "query",
            map("q", query, "category", category, "provider", provider),
            "items",
            new ArrayList<>(cards.values()),
            "nextCursor",
            nextCursor,
            "partial",
            responses.stream().anyMatch(value -> !value.state().equals("ok")),
            "providers",
            statuses);
    return result;
  }

  private Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second) {
    var rank = Map.of("external-only", 0, "preview", 1, "download", 2);
    var a = (Map<String, Object>) first.get("availability");
    var b = (Map<String, Object>) second.get("availability");
    boolean prefer = rank.get(b.get("kind")) > rank.get(a.get("kind"));
    var primary = prefer ? second : first;
    var other = prefer ? first : second;
    var result = new LinkedHashMap<>(primary);
    for (String key : List.of("title", "description"))
      if (other.get(key).toString().length() > primary.get(key).toString().length())
        result.put(key, other.get(key));
    if (((List<?>) primary.get("authors")).isEmpty()) result.put("authors", other.get("authors"));
    if (primary.get("coverUrl") == null) result.put("coverUrl", other.get("coverUrl"));
    var providers = new TreeSet<String>((List<String>) first.get("providers"));
    providers.addAll((List<String>) second.get("providers"));
    result.put("providers", List.copyOf(providers));
    var subjects = new LinkedHashSet<String>((List<String>) first.get("subjects"));
    subjects.addAll((List<String>) second.get("subjects"));
    result.put("subjects", subjects.stream().limit(20).toList());
    long ac = first.get("ratingsCount") instanceof Number value ? value.longValue() : -1,
        bc = second.get("ratingsCount") instanceof Number value ? value.longValue() : -1;
    if (bc > ac) {
      result.put("rating", second.get("rating"));
      result.put("ratingsCount", second.get("ratingsCount"));
    } else {
      result.put("rating", first.get("rating"));
      result.put("ratingsCount", first.get("ratingsCount"));
    }
    return result;
  }

  public synchronized Map<String, Object> trending(
      String identity, String window, String provider, String category) {
    rate(identity);
    category(category);
    if (!Set.of("week", "month").contains(window)
        || !Set.of("combined", "openlibrary").contains(provider))
      throw new ApiException(422, "Invalid trending request");
    String key = "openlibrary-" + window;
    var snapshot = mapper.snapshot(key);
    boolean stale = false;
    Instant now = Instant.now();
    if (snapshot == null || snapshot.fetchedAt().plusSeconds(43200).isBefore(now))
      try {
        var response =
            providerJson(
                "https://openlibrary.org/trending/"
                    + (window.equals("week") ? "weekly" : "monthly")
                    + ".json?limit=20",
                false);
        var rows = response.path("works");
        if (!rows.isArray() || rows.size() > 50)
          throw new ApiException(502, "Invalid trending response");
        var cards = new ArrayList<Map<String, Object>>();
        for (var row : rows) cards.add(CatalogCards.openLibrary(row));
        snapshot =
            new CatalogSnapshot(
                key, json.writeValueAsString(cards), now, now.plusSeconds(7 * 86400));
        mapper.save(snapshot);
      } catch (Exception error) {
        if (snapshot == null || snapshot.expiresAt().isBefore(now))
          throw new ApiException(
              503, "SOURCE_TEMPORARILY_UNAVAILABLE", "Trending books are temporarily unavailable");
        stale = true;
      }
    try {
      List<Map<String, Object>> items =
          json.readValue(snapshot.itemsJson(), new TypeReference<>() {});
      if (category != null) {
        String expected = category.replace('-', ' ');
        items =
            items.stream()
                .filter(card ->
                    ((List<String>) card.get("subjects"))
                        .stream()
                            .anyMatch(subject ->
                                subject
                                    .toLowerCase(Locale.ROOT)
                                    .replace('-', ' ')
                                    .contains(expected)))
                .toList();
      }
      return map(
          "window",
          window,
          "provider",
          provider,
          "fetchedAt",
          snapshot.fetchedAt(),
          "stale",
          stale,
          "nextRefreshAt",
          stale ? now.plusSeconds(900) : snapshot.fetchedAt().plusSeconds(43200),
          "items",
          items);
    } catch (java.io.IOException error) {
      throw new ApiException(503, "Invalid catalog snapshot");
    }
  }

  public Map<String, Object> importOffer(
      String user, String provider, String external, String offer) {
    rate(user);
    if (!provider.equals("googlebooks")
        || googleKey.isBlank()
        || external.isBlank()
        || external.length() > 256)
      throw new ApiException(
          422, "DOWNLOAD_NOT_AUTHORIZED", "Catalog source cannot authorize this download");
    String format =
        offer.equals("googlebooks:" + external + ":epub")
            ? "epub"
            : offer.equals("googlebooks:" + external + ":pdf") ? "pdf" : null;
    if (format == null)
      throw new ApiException(422, "DOWNLOAD_NOT_AUTHORIZED", "Invalid catalog offer");
    var volume =
        providerJson("https://www.googleapis.com/books/v1/volumes/" + encode(external), true);
    if (!volume.path("id").asText().equals(external)
        || !CatalogCards.googleFormats(volume).contains(format))
      throw new ApiException(
          422, "DOWNLOAD_NOT_AUTHORIZED", "This volume has no verified public-domain download");
    String url =
        CatalogCards.googleUrl(
            volume.path("accessInfo").path(format).path("downloadLink").asText(null));
    if (url == null)
      throw new ApiException(422, "DOWNLOAD_NOT_AUTHORIZED", "Unapproved download URL");
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("catalogProvider", "googlebooks");
    metadata.put("catalogExternalId", external);
    metadata.put("requestedFormat", format);
    var card = CatalogCards.google(volume);
    metadata.put("creator", card.get("authors"));
    if (card.get("coverUrl") != null) metadata.put("catalogCoverUrl", card.get("coverUrl"));
    return imports.create(
        user,
        url,
        CatalogCards.plain(volume.path("volumeInfo").path("title").asText(), 300),
        "catalog-offer",
        metadata);
  }
}
