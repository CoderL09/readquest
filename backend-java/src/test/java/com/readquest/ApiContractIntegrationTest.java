package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.CatalogService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:api_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean CatalogService catalog;

  @Test
  void validationErrorsContainTraceAndFieldNamesButNeverRejectedValues() throws Exception {
    String secret = "synthetic-secret-never-echo";
    var response =
        mvc.perform(
                post("/api/users")
                    .header("X-Request-ID", "trace-contract-1")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email", "not-an-email", "password", secret, "displayName", ""))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(header().string("X-Request-ID", "trace-contract-1"))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.requestId").value("trace-contract-1"))
            .andExpect(jsonPath("$.error.details").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(response.contains(secret));
    assertFalse(response.contains("not-an-email"));
  }

  @Test
  void unexpectedErrorsDoNotLeakProviderOrDatabaseDetails() throws Exception {
    when(catalog.categories())
        .thenThrow(new IllegalStateException("synthetic-internal-credential"));
    String body =
        mvc.perform(get("/api/catalog/categories"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(header().exists("X-Request-ID"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(body.contains("synthetic-internal-credential"));
  }

  @Test
  void openApiDescribesBearerSecurityAndNativeStreamResponses() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.paths['/api/book-notes'].get.responses['422']").exists())
        .andExpect(jsonPath("$.paths['/api/books/{book}/reading-conversations'].post").exists());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void authenticationErrorsAlsoUseTheTraceableErrorContract() throws Exception {
    mvc.perform(get("/api/book-notes").header("X-Request-ID", "trace-unauthorized"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.requestId").value("trace-unauthorized"));
  }

  @Test
  void methodErrorsPreserveTheRestAllowHeader() throws Exception {
    var account =
        json.readTree(
            mvc.perform(
                    post("/api/users")
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "email",
                                    UUID.randomUUID() + "@example.test",
                                    "password",
                                    "Synthetic123!",
                                    "displayName",
                                    "Contract reader"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(
            put("/api/book-notes")
                .header("Authorization", "Bearer " + account.path("accessToken").asText()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
        .andExpect(jsonPath("$.error.code").value("HTTP_405"));
  }
}
