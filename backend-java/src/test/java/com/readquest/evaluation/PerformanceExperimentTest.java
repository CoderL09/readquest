package com.readquest.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.infrastructure.*;
import com.readquest.mapper.MembershipMapper;
import com.readquest.service.impl.MembershipServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.jfr.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit local experiment, never a load test of production or third-party services. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"server.address=127.0.0.1", "readquest.redis.enabled=false"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PERFORMANCE_EXPERIMENT_ENABLED", matches = "true")
class PerformanceExperimentTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry props) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      props.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:performance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired MembershipMapper membershipMapper;
  @Autowired PlatformTransactionManager manager;
  private final Path output = Path.of("target/performance");

  private Map<String, Object> measure(
      String name, int concurrency, int count, Callable<Boolean> operation) throws Exception {
    var timings = new ConcurrentLinkedQueue<Long>();
    var errors = new AtomicInteger();
    long started = System.nanoTime();
    try (var pool = Executors.newFixedThreadPool(concurrency)) {
      var jobs = new ArrayList<Callable<Void>>();
      for (int i = 0; i < count; i++)
        jobs.add(() -> {
          long before = System.nanoTime();
          try {
            if (!operation.call()) errors.incrementAndGet();
          } catch (Exception e) {
            errors.incrementAndGet();
          } finally {
            timings.add((System.nanoTime() - before) / 1000000);
          }
          return null;
        });
      for (var future : pool.invokeAll(jobs)) future.get();
    }
    double seconds = (System.nanoTime() - started) / 1e9;
    var values = new ArrayList<>(timings);
    return Map.of(
        "scenario",
        name,
        "concurrency",
        concurrency,
        "requests",
        count,
        "errors",
        errors.get(),
        "throughputPerSecond",
        count / seconds,
        "p50Ms",
        RetrievalMetrics.percentile(values, .5),
        "p95Ms",
        RetrievalMetrics.percentile(values, .95));
  }

  @Test
  void recordHttpCacheAndRealRedisFaultExperiments() throws Exception {
    Files.createDirectories(output);
    var results = new ArrayList<Map<String, Object>>();
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var recording = new Recording(Configuration.getConfiguration("profile"))) {
      recording.setMaxSize(64 * 1024 * 1024);
      recording.disable("jdk.InitialEnvironmentVariable");
      recording.disable("jdk.InitialSystemProperty");
      recording.disable("jdk.SystemProcess");
      recording.start();
      String email = UUID.randomUUID() + "@example.test";
      var registration =
          client.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          json.writeValueAsString(
                              Map.of(
                                  "email",
                                  email,
                                  "displayName",
                                  "Performance fixture",
                                  "password",
                                  "Password123!"))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(201, registration.statusCode());
      var auth = json.readTree(registration.body());
      String user = auth.path("user").path("id").asText(),
          token = auth.path("accessToken").asText();
      for (int i = 0; i < 120; i++) {
        String book = UUID.randomUUID().toString();
        db.update(
            "INSERT INTO books(id,owner_id,title,status,visibility,asset_scope,created_at,updated_at) VALUES(?,?,?,'completed','private','private',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
            book,
            user,
            "Performance fixture " + i);
        db.update(
            "INSERT INTO library_entries(user_id,book_id,shelf_state,created_at,updated_at) VALUES(?,?,'saved',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))",
            user,
            book);
      }
      var request =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:"
                          + port
                          + "/api/users/me/library/books?page=1&pageSize=20"))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();
      for (int i = 0; i < 20; i++)
        assertEquals(
            200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
      int requests = Integer.parseInt(System.getenv().getOrDefault("PERFORMANCE_REQUESTS", "1200"));
      if (requests < 100 || requests > 20000)
        throw new IllegalArgumentException("Performance requests must be 100..20000");
      for (int concurrency : List.of(1, 10, 30))
        results.add(
            measure(
                "authenticated-library-http",
                concurrency,
                requests,
                () ->
                    client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
                        == 200));
      var provider = mock(ObjectProvider.class);
      var meters = new SimpleMeterRegistry();
      try {
        var cache = new CatalogResultCache(provider, json, meters);
        var loads = new AtomicInteger();
        java.util.function.Supplier<Map<String, Object>> loader =
            () -> {
              loads.incrementAndGet();
              try {
                Thread.sleep(20);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return Map.of("items", List.of(Map.of("title", "Synthetic catalog card")));
            };
        results.add(
            measure(
                "catalog-same-key-cold-local-flight",
                30,
                120,
                () -> !cache.getOrLoad("perf-shared", loader).isEmpty()));
        assertEquals(1, loads.get());
        results.add(
            measure(
                "catalog-hot-local-cache",
                30,
                120,
                () -> !cache.getOrLoad("perf-shared", loader).isEmpty()));
        assertEquals(1, loads.get());
      } finally {
        meters.close();
      }
      results.add(redisFault());
      recording.stop();
      recording.dump(output.resolve("readquest-profile.jfr"));
    }
    var samples = new HashMap<String, Integer>();
    int executions = 0, gcPauses = 0;
    long gcPauseNanos = 0;
    try (var events = new jdk.jfr.consumer.RecordingFile(output.resolve("readquest-profile.jfr"))) {
      while (events.hasMoreEvents()) {
        var event = events.readEvent();
        String type = event.getEventType().getName();
        if (type.equals("jdk.ExecutionSample")) {
          executions++;
          var stack = event.getStackTrace();
          if (stack != null && !stack.getFrames().isEmpty()) {
            var method = stack.getFrames().getFirst().getMethod();
            samples.merge(method.getType().getName() + "." + method.getName(), 1, Integer::sum);
          }
        }
        if (type.equals("jdk.GCPhasePause")) {
          gcPauses++;
          gcPauseNanos += event.getDuration().toNanos();
        }
      }
    }
    var hotspots =
        samples.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(e -> Map.of("method", e.getKey(), "samples", e.getValue()))
            .toList();
    json.writerWithDefaultPrettyPrinter()
        .writeValue(
            output.resolve("jfr-analysis.json").toFile(),
            Map.of(
                "executionSamples",
                executions,
                "gcPauseEvents",
                gcPauses,
                "gcPauseTotalMs",
                gcPauseNanos / 1000000.0,
                "topSampledMethods",
                hotspots,
                "interpretation",
                "Local short-run baseline; sampled hotspots include test instrumentation and do not prove production bottlenecks."));
    var report = new LinkedHashMap<String, Object>();
    report.put("java", System.getProperty("java.version"));
    report.put("processors", Runtime.getRuntime().availableProcessors());
    try (var connection = db.getDataSource().getConnection()) {
      report.put("database", connection.getMetaData().getDatabaseProductName());
    }
    report.put(
        "scope",
        "local synthetic fixtures; no external APIs; HTTP includes authentication; cache experiment is component-level");
    report.put("results", results);
    json.writerWithDefaultPrettyPrinter()
        .writeValue(output.resolve("summary.json").toFile(), report);
    for (var row : results)
      if (row.containsKey("errors")) assertEquals(0, ((Number) row.get("errors")).intValue());
  }

  private Map<String, Object> redisFault() throws Exception {
    int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "16379"));
    var config = new RedisStandaloneConfiguration("127.0.0.1", redisPort);
    String password = System.getenv("REDIS_TEST_PASSWORD");
    if (password != null) config.setPassword(password);
    var options =
        LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(200))
            .shutdownTimeout(Duration.ZERO)
            .build();
    var healthy = new LettuceConnectionFactory(config, options);
    healthy.afterPropertiesSet();
    healthy.start();
    int closedPort;
    try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }
    var failed =
        new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", closedPort), options);
    failed.afterPropertiesSet();
    failed.start();
    var metrics = new SimpleMeterRegistry();
    String identity = "fault-" + UUID.randomUUID();
    try {
      var up = new RedisBackend(new StringRedisTemplate(healthy), metrics);
      var down = new RedisBackend(new StringRedisTemplate(failed), metrics);
      ObjectProvider<RedisBackend> available = mock(ObjectProvider.class),
          unavailable = mock(ObjectProvider.class);
      when(available.getIfAvailable()).thenReturn(up);
      when(unavailable.getIfAvailable()).thenReturn(down);
      var online = new MembershipServiceImpl(membershipMapper, manager, available, "20");
      var offline = new MembershipServiceImpl(membershipMapper, manager, unavailable, "20");
      assertTrue(online.rateLimit("fault-experiment", identity, 3, 3600));
      assertTrue(online.rateLimit("fault-experiment", identity, 3, 3600));
      var admitted = new AtomicInteger();
      var load =
          measure(
              "redis-unreachable-database-fallback",
              10,
              20,
              () -> {
                if (offline.rateLimit("fault-experiment", identity, 3, 3600))
                  admitted.incrementAndGet();
                return true;
              });
      assertEquals(1, admitted.get());
      assertFalse(online.rateLimit("fault-experiment", identity, 3, 3600));
      up.put("rq:experiment:" + identity, "restored", Duration.ofSeconds(30));
      assertEquals("restored", up.get("rq:experiment:" + identity).orElseThrow());
      var out = new LinkedHashMap<>(load);
      out.put("faultInjection", "closed localhost TCP endpoint; separate Redis client");
      out.put("admittedBeforeFault", 2);
      out.put("admittedDuringFault", admitted.get());
      out.put("limit", 3);
      out.put("redisAvailableAfterFault", true);
      return out;
    } finally {
      healthy.destroy();
      failed.destroy();
      metrics.close();
    }
  }
}
