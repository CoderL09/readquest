package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.infrastructure.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "REDIS_INTEGRATION_ENABLED", matches = "true")
class RedisBackendIntegrationTest {
  static LettuceConnectionFactory factory;
  static StringRedisTemplate template;
  static SimpleMeterRegistry metrics;
  static RedisBackend redis;

  @BeforeAll
  static void connect() {
    var config =
        new RedisStandaloneConfiguration(
            System.getenv().getOrDefault("REDIS_TEST_HOST", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "16379")));
    String password = System.getenv("REDIS_TEST_PASSWORD");
    if (password != null && !password.isBlank()) config.setPassword(password);
    factory = new LettuceConnectionFactory(config);
    factory.afterPropertiesSet();
    factory.start();
    template = new StringRedisTemplate(factory);
    metrics = new SimpleMeterRegistry();
    redis = new RedisBackend(template, metrics);
    assertEquals(
        "PONG",
        template.execute(
            (org.springframework.data.redis.core.RedisCallback<String>)
                connection -> connection.ping()));
  }

  @AfterAll
  static void close() {
    if (factory != null) factory.destroy();
    if (metrics != null) metrics.close();
  }

  @Test
  void luaLimitIsAtomicAndSetsExpiry() throws Exception {
    String key = "rq:optimization-test:" + UUID.randomUUID();
    long expires = System.currentTimeMillis() + 60000;
    try (var threads = Executors.newFixedThreadPool(12)) {
      var tasks = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 80; i++) tasks.add(() -> redis.allow(key, 7, expires).orElseThrow());
      long admitted = 0;
      for (var result : threads.invokeAll(tasks)) if (result.get()) admitted++;
      assertEquals(7, admitted);
      assertTrue(template.getExpire(key, TimeUnit.MILLISECONDS) > 0);
      assertTrue(template.getExpire(key, TimeUnit.MILLISECONDS) <= 60000);
    } finally {
      template.delete(key);
    }
  }

  @Test
  void anotherOwnerCannotReleaseTheLoadLease() {
    String key = "rq:optimization-test:" + UUID.randomUUID();
    try {
      assertEquals(Optional.of(true), redis.acquire(key, "first"));
      redis.release(key, "other");
      assertEquals(Optional.of(false), redis.acquire(key, "second"));
      redis.release(key, "first");
      assertEquals(Optional.of(true), redis.acquire(key, "second"));
    } finally {
      template.delete(key);
    }
  }

  @Test
  void catalogSharesJsonCacheAcrossInstancesWithoutRepeatingTheLoader() throws Exception {
    ObjectProvider<RedisBackend> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redis);
    var first = new CatalogResultCache(provider, new ObjectMapper(), metrics);
    var second = new CatalogResultCache(provider, new ObjectMapper(), metrics);
    var calls = new AtomicInteger();
    String query = UUID.randomUUID().toString();
    var result =
        Map.<String, Object>of(
            "items", List.of(Map.of("title", "Synthetic public card")), "partial", false);
    try (var threads = Executors.newFixedThreadPool(2)) {
      var loader =
          (java.util.function.Supplier<Map<String, Object>>)
              () -> {
                calls.incrementAndGet();
                try {
                  Thread.sleep(100);
                } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(error);
                }
                return result;
              };
      var one = threads.submit(() -> first.getOrLoad(query, loader));
      var two = threads.submit(() -> second.getOrLoad(query, loader));
      assertEquals(result, one.get(5, TimeUnit.SECONDS));
      assertEquals(result, two.get(5, TimeUnit.SECONDS));
      assertEquals(1, calls.get());
      String key = "rq:catalog:v1:" + com.readquest.catalog.CatalogCards.hash(query);
      assertTrue(template.getExpire(key, TimeUnit.SECONDS) > 0);
      template.delete(key);
    }
  }
}
