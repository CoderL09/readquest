package com.readquest.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** String/JSON-only Redis operations. Failure never replaces authoritative database state. */
@Component
@ConditionalOnProperty(name = "readquest.redis.enabled", havingValue = "true")
public class RedisBackend {
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then redis.call('PEXPIREAT', KEYS[1], ARGV[2]) end
          if count <= tonumber(ARGV[1]) then return 1 end
          return 0
          """,
          Long.class);
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          """
          if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
          return 0
          """,
          Long.class);
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final AtomicLong retryAfter = new AtomicLong();

  public RedisBackend(StringRedisTemplate redis, MeterRegistry metrics) {
    this.redis = redis;
    this.metrics = metrics;
  }

  private <T> Optional<T> call(String operation, Supplier<T> action) {
    long retry = retryAfter.get();
    if (retry != 0 && System.nanoTime() - retry < 0) {
      metrics
          .counter("readquest.redis.operations", "operation", operation, "outcome", "cooldown")
          .increment();
      return Optional.empty();
    }
    try {
      T value = action.get();
      metrics
          .counter("readquest.redis.operations", "operation", operation, "outcome", "success")
          .increment();
      return Optional.ofNullable(value);
    } catch (DataAccessException error) {
      retryAfter.set(System.nanoTime() + Duration.ofSeconds(5).toNanos());
      metrics
          .counter("readquest.redis.operations", "operation", operation, "outcome", "unavailable")
          .increment();
      return Optional.empty();
    }
  }

  public Optional<String> get(String key) {
    return call("cache_get", () -> redis.opsForValue().get(key));
  }

  public void put(String key, String value, Duration ttl) {
    call(
        "cache_put",
        () -> {
          redis.opsForValue().set(key, value, ttl);
          return true;
        });
  }

  public Optional<Boolean> acquire(String key, String token) {
    return call(
        "lease_acquire", () -> redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(60)));
  }

  public void release(String key, String token) {
    call("lease_release", () -> redis.execute(RELEASE, List.of(key), token));
  }

  public Optional<Boolean> allow(String key, int limit, long expiresAtMillis) {
    return call(
        "rate_limit",
        () -> {
          Long result =
              redis.execute(
                  LIMIT, List.of(key), Integer.toString(limit), Long.toString(expiresAtMillis));
          return result == null ? null : result == 1;
        });
  }
}
