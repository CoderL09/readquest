package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "GOOGLE_BOOK_API=local-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      p.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:catalog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired CatalogService catalog;
  @MockitoBean RemoteFetcher fetcher;
  boolean publicDomain = true, failGoogle = false, failOpen = false;
  long googleTotal = 1;

  JsonNode volume() throws Exception {
    return json.readTree(
        "{\"id\":\"volume1\",\"volumeInfo\":{\"title\":\"River Story\",\"authors\":[\"A"
            + " Writer\"],\"industryIdentifiers\":[{\"type\":\"ISBN_13\",\"identifier\":\"9780306406157\"}]},\"accessInfo\":{\"publicDomain\":"
            + publicDomain
            + ",\"accessViewStatus\":\"FULL_PUBLIC_DOMAIN\",\"epub\":{\"isAvailable\":true,\"downloadLink\":\"https://books.google.com/download?volume=volume1\"}}}");
  }

  @BeforeEach
  void setup() throws Exception {
    publicDomain = true;
    failGoogle = false;
    failOpen = false;
    db.update("DELETE FROM catalog_snapshots");
    when(fetcher.fetch(anyString(), anyMap()))
        .thenAnswer(call -> {
          String url = call.getArgument(0);
          boolean google = url.contains("googleapis.com");
          if (google && failGoogle || !google && failOpen)
            throw new com.readquest.exception.ApiException(
                503, "SOURCE_TEMPORARILY_UNAVAILABLE", "Test source failure");
          JsonNode value;
          if (google) {
            Map<String, String> headers = call.getArgument(1);
            assertEquals("local-test-key", headers.get("x-goog-api-key"));
            assertFalse(url.contains("local-test-key"));
            if (url.contains("/volumes/volume1")) value = volume();
            else {
              var result = json.createObjectNode().put("totalItems", googleTotal);
              result.putArray("items").add(volume());
              value = result;
            }
          } else
            value =
                json.readTree(
                    url.contains("trending")
                        ? "{\"works\":[{\"key\":\"/works/OL123W\",\"title\":\"River"
                            + " Story\",\"author_name\":[\"A Writer\"]}]}"
                        : "{\"numFound\":1,\"docs\":[{\"key\":\"/works/OL123W\",\"title\":\"River"
                            + " Story\",\"author_name\":[\"A"
                            + " Writer\"],\"isbn\":[\"9780306406157\"],\"ebook_access\":\"borrowable\"}]}");
          return new RemoteFetcher.Resource(
              URI.create(url), "application/json", "response.json", json.writeValueAsBytes(value));
        });
  }

  @Test
  void mergesEquivalentBooksAndCachesSearchWithoutInventingDownloads() throws Exception {
    String query = UUID.randomUUID().toString();
    var result = catalog.search("test", query, null, "combined", null, 20);
    var items = (List<?>) result.get("items");
    assertEquals(1, items.size());
    var card = (Map<?, ?>) items.getFirst();
    assertEquals(List.of("googlebooks", "openlibrary"), card.get("providers"));
    assertEquals("download", ((Map<?, ?>) card.get("availability")).get("kind"));
    catalog.search("test", query, null, "combined", null, 20);
    verify(fetcher, times(2)).fetch(anyString(), anyMap());
    publicDomain = false;
    result = catalog.search("test", UUID.randomUUID().toString(), null, "googlebooks", null, 20);
    card = (Map<?, ?>) ((List<?>) result.get("items")).getFirst();
    assertNotEquals("download", ((Map<?, ?>) card.get("availability")).get("kind"));
  }

  @Test
  void returnsPartialSourceFailureAndRejectsCursorForAnotherQuery() {
    failGoogle = true;
    var result = catalog.search("test", UUID.randomUUID().toString(), null, "combined", null, 20);
    assertEquals(true, result.get("partial"));
    assertFalse(((List<?>) result.get("items")).isEmpty());
    failGoogle = false;
    googleTotal = 100;
    String query = UUID.randomUUID().toString();
    result = catalog.search("test", query, null, "googlebooks", null, 2);
    String cursor = (String) result.get("nextCursor");
    assertNotNull(cursor);
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> catalog.search("test", "another query", null, "googlebooks", cursor, 2));
  }

  @Test
  void keepsRealTrendingSnapshotWhenSourceFails() {
    var value = catalog.trending("test", "week", "openlibrary", null);
    assertEquals(false, value.get("stale"));
    db.update(
        "UPDATE catalog_snapshots SET fetched_at=TIMESTAMPADD(HOUR,-13,CURRENT_TIMESTAMP) WHERE"
            + " cache_key='openlibrary-week'");
    failOpen = true;
    value = catalog.trending("test", "week", "openlibrary", null);
    assertEquals(true, value.get("stale"));
    assertEquals(1, ((List<?>) value.get("items")).size());
  }

  @Test
  void rechecksPublicDomainBeforeAdmittingCatalogDownload() throws Exception {
    var response =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "displayName",
                                "Catalog Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    String body =
        "{\"providerId\":\"googlebooks\",\"externalId\":\"volume1\",\"offerId\":\"googlebooks:volume1:epub\"}";
    publicDomain = false;
    mvc.perform(
            post("/api/catalog-imports")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isUnprocessableEntity());
    publicDomain = true;
    mvc.perform(
            post("/api/catalog-imports")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.job.kind").value("catalog-offer"));
    mvc.perform(get("/api/catalog/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(11));
  }
}
