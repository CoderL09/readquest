package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.infrastructure.*;
import com.readquest.mapper.MembershipMapper;
import com.readquest.service.impl.MembershipServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CacheAndRateLimitTest {
  @Test
  void cacheCoalescesConcurrentRequestsAndDoesNotCacheLoaderFailures() throws Exception {
    ObjectProvider<RedisBackend> provider = mock(ObjectProvider.class);
    var cache = new CatalogResultCache(provider, new ObjectMapper(), new SimpleMeterRegistry());
    var calls = new AtomicInteger();
    try (var threads = Executors.newFixedThreadPool(12)) {
      var tasks = new ArrayList<Callable<Map<String, Object>>>();
      for (int i = 0; i < 30; i++)
        tasks.add(() ->
            cache.getOrLoad(
                "one",
                () -> {
                  calls.incrementAndGet();
                  try {
                    Thread.sleep(80);
                  } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                  }
                  return Map.of("items", List.of());
                }));
      for (var result : threads.invokeAll(tasks))
        assertEquals(Map.of("items", List.of()), result.get());
      assertEquals(1, calls.get());
    }
    assertThrows(
        IllegalStateException.class,
        () ->
            cache.getOrLoad(
                "retry",
                () -> {
                  throw new IllegalStateException("provider unavailable");
                }));
    assertEquals(
        Map.of("items", List.of()), cache.getOrLoad("retry", () -> Map.of("items", List.of())));
  }

  @Test
  void redisOutageOpensCooldownInsteadOfBlockingEveryRequest() {
    var template = mock(StringRedisTemplate.class);
    var values = mock(org.springframework.data.redis.core.ValueOperations.class);
    when(template.opsForValue()).thenReturn(values);
    when(values.get("key")).thenThrow(new RedisConnectionFailureException("offline"));
    var backend = new RedisBackend(template, new SimpleMeterRegistry());
    assertEquals(Optional.empty(), backend.get("key"));
    assertEquals(Optional.empty(), backend.get("key"));
    verify(values, times(1)).get("key");
  }

  @Test
  void redisFailureCannotResetTheDurableRateLimit() {
    var mapper = mock(MembershipMapper.class);
    var backend = mock(RedisBackend.class);
    ObjectProvider<RedisBackend> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(backend);
    when(backend.allow(anyString(), anyInt(), anyLong())).thenReturn(Optional.empty());
    when(mapper.hits(anyString())).thenReturn(21);
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    var service = new MembershipServiceImpl(mapper, manager, provider, "");
    assertFalse(service.rateLimit("auth", "synthetic-user", 20, 900));
    verify(mapper).hit(anyString(), anyLong(), any());
  }

  @Test
  void redisFastRejectionDoesNotOpenADatabaseTransaction() {
    var mapper = mock(MembershipMapper.class);
    var backend = mock(RedisBackend.class);
    ObjectProvider<RedisBackend> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(backend);
    when(backend.allow(anyString(), anyInt(), anyLong())).thenReturn(Optional.of(false));
    var manager = mock(PlatformTransactionManager.class);
    var service = new MembershipServiceImpl(mapper, manager, provider, "");
    assertFalse(service.rateLimit("auth", "synthetic-user", 20, 900));
    verifyNoInteractions(mapper, manager);
  }

  @Test
  void pageBoundsAreCheckedBeforeCalculatingOffsets() {
    assertEquals(0, new com.readquest.dto.PageQuery(1, 20).offset());
    assertThrows(
        com.readquest.exception.ApiException.class, () -> new com.readquest.dto.PageQuery(0, 20));
    assertThrows(
        com.readquest.exception.ApiException.class, () -> new com.readquest.dto.PageQuery(1, 101));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> new com.readquest.dto.PageQuery(Integer.MAX_VALUE, 100));
  }
}
