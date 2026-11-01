package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      "readquest.management-token=operations-test-only-token-at-least-32-characters"
    })
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability(
    tracing = false)
class OperationsIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:operations;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @LocalManagementPort int port;
  final HttpClient client = HttpClient.newHttpClient();

  private HttpResponse<String> get(String path, String token) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    if (token != null) request.header("Authorization", "Bearer " + token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void healthIsPublicWithoutSensitiveDetails() throws Exception {
    var response = get("/actuator/health/readiness", null);
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("UP"));
    assertFalse(response.body().contains("jdbc"));
  }

  @Test
  void metricsRequireTheDedicatedOperationsToken() throws Exception {
    assertEquals(401, get("/actuator/metrics", null).statusCode());
    assertEquals(401, get("/actuator/metrics", "ordinary-application-token").statusCode());
    var response = get("/actuator/metrics", "operations-test-only-token-at-least-32-characters");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("jvm"));
    assertEquals(
        200,
        get("/actuator/prometheus", "operations-test-only-token-at-least-32-characters")
            .statusCode());
  }

  @Test
  void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
    var response = get("/actuator/env", "operations-test-only-token-at-least-32-characters");
    assertNotEquals(200, response.statusCode());
    assertFalse(response.body().contains("operations-test-only-token"));
  }
}
