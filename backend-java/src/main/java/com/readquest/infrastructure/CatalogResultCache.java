package com.readquest.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.catalog.CatalogCards;
import com.readquest.exception.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Only public catalog cards enter this cache; signed book assets and user data never do. */
@Component
public class CatalogResultCache {
  private record Entry(long expiresAt, Map<String, Object> value) {}

  private final Map<String, Entry> local = new LinkedHashMap<>(32, .75f, true);
  private final ConcurrentMap<String, CompletableFuture<Map<String, Object>>> flights =
      new ConcurrentHashMap<>();
  private final Semaphore capacity = new Semaphore(128);
  private final RedisBackend redis;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  public CatalogResultCache(
      ObjectProvider<RedisBackend> redis, ObjectMapper json, MeterRegistry metrics) {
    this.redis = redis.getIfAvailable();
    this.json = json;
    this.metrics = metrics;
  }

  private Map<String, Object> get(String key) {
    synchronized (local) {
      var value = local.get(key);
      if (value != null) {
        if (value.expiresAt() > System.currentTimeMillis()) {
          metrics.counter("readquest.catalog.cache", "outcome", "local_hit").increment();
          return value.value();
        }
        local.remove(key);
      }
    }
    if (redis != null) {
      var stored = redis.get(key);
      if (stored.isPresent())
        try {
          Map<String, Object> value = json.readValue(stored.get(), new TypeReference<>() {});
          if (!(value.get("items") instanceof List<?>)) return null;
          // The shared cache's TTL remains authoritative; do not renew an entry in L1.
          metrics.counter("readquest.catalog.cache", "outcome", "redis_hit").increment();
          return value;
        } catch (java.io.IOException ignored) {
          metrics.counter("readquest.catalog.cache", "outcome", "invalid_entry").increment();
        }
    }
    return null;
  }

  private void put(String key, Map<String, Object> value) {
    long seconds =
        Boolean.TRUE.equals(value.get("partial"))
            ? 15
            : value.get("items") instanceof List<?> items && items.isEmpty() ? 20 : 300;
    seconds += ThreadLocalRandom.current().nextInt((int) Math.max(1, seconds / 10));
    synchronized (local) {
      local
          .entrySet()
          .removeIf(entry -> entry.getValue().expiresAt() <= System.currentTimeMillis());
      while (local.size() >= 250) local.remove(local.keySet().iterator().next());
      local.put(key, new Entry(System.currentTimeMillis() + seconds * 1000, value));
    }
    if (redis != null)
      try {
        redis.put(key, json.writeValueAsString(value), Duration.ofSeconds(seconds));
      } catch (java.io.IOException ignored) {
        metrics.counter("readquest.catalog.cache", "outcome", "serialization_failure").increment();
      }
  }

  public Map<String, Object> getOrLoad(String rawKey, Supplier<Map<String, Object>> loader) {
    String key = "rq:catalog:v1:" + CatalogCards.hash(rawKey);
    var found = get(key);
    if (found != null) return found;
    var own = new CompletableFuture<Map<String, Object>>();
    var prior = flights.putIfAbsent(key, own);
    if (prior != null)
      try {
        return prior.get(40, TimeUnit.SECONDS);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw busy();
      } catch (ExecutionException error) {
        if (error.getCause() instanceof RuntimeException runtime) throw runtime;
        throw busy();
      } catch (TimeoutException error) {
        throw busy();
      }
    String lock = "rq:catalog-lock:v1:" + CatalogCards.hash(rawKey),
        token = UUID.randomUUID().toString();
    boolean leased = false, admitted = false;
    try {
      admitted = capacity.tryAcquire();
      if (!admitted) throw busy();
      found = get(key);
      if (found != null) {
        own.complete(found);
        return found;
      }
      if (redis != null) {
        var claim = redis.acquire(lock, token);
        leased = claim.orElse(false);
        if (claim.isPresent() && !claim.get()) {
          long deadline = System.nanoTime() + Duration.ofMillis(1500).toNanos();
          do {
            found = get(key);
            if (found != null) {
              own.complete(found);
              return found;
            }
            Thread.sleep(40);
          } while (System.nanoTime() < deadline);
          throw busy();
        }
      }
      metrics.counter("readquest.catalog.cache", "outcome", "load").increment();
      var loaded = loader.get();
      put(key, loaded);
      own.complete(loaded);
      return loaded;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      var failure = busy();
      own.completeExceptionally(failure);
      throw failure;
    } catch (RuntimeException | Error error) {
      own.completeExceptionally(error);
      throw error;
    } finally {
      if (leased) redis.release(lock, token);
      if (admitted) capacity.release();
      flights.remove(key, own);
    }
  }

  private ApiException busy() {
    return new ApiException(503, "CATALOG_BUSY", "Catalog is busy; retry shortly");
  }
}
