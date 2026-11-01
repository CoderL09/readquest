package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.imports.*;
import com.readquest.service.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemoteImportsIntegrationTest {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:remote-imports;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired RemoteImportService remote;
  @Autowired IngestionService imports;
  @MockitoBean RemoteFetcher fetcher;
  String token, user;
  final String url = "https://example.com/article";
  final String article =
      "<html><head><title>Site title</title></head><body><nav>Ignore me</nav><article><h1>River"
          + " Story</h1><p>We walked along the quiet river before the sun rose over the valley. The"
          + " children watched the water and listened carefully to the"
          + " birds.</p><script>bad()</script><img"
          + " src='http://127.0.0.1/private'></article></body></html>";

  @BeforeEach
  void setup() throws Exception {
    var result =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "displayName",
                                "Importer",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(result.getResponse().getContentAsString());
    token = "Bearer " + auth.path("accessToken").asText();
    user = auth.path("user").path("id").asText();
    when(fetcher.fetch(anyString()))
        .thenReturn(
            new RemoteFetcher.Resource(
                URI.create(url),
                "text/html",
                "article.html",
                article.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void inspectsThenImportsReadableArticleWithSourceMetadata() throws Exception {
    mvc.perform(
            post("/api/source-inspections")
                .header("Authorization", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("url", url))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inspection.preview.title").value("River Story"));
    var created = remote.create(user, url, "My article", "web-article");
    String id = ((Map<?, ?>) created.get("job")).get("id").toString();
    remote.processPending();
    imports.processPending();
    var job = remote.get(user, id);
    assertEquals("completed", job.get("status"));
    String book = job.get("bookId").toString();
    mvc.perform(get("/api/books/" + book).header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("My article"))
        .andExpect(jsonPath("$.metadata.sourceUrl").value(url));
    var chapters =
        mvc.perform(get("/api/books/" + book + "/chapters").header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn();
    String html =
        json.readTree(chapters.getResponse().getContentAsString())
            .get(0)
            .path("contentHtml")
            .asText();
    assertFalse(html.contains("script"));
    assertFalse(html.contains("127.0.0.1"));
    assertTrue(html.contains("quiet river"));
    assertEquals(true, remote.create(user, url, null, "web-article").get("deduplicated"));
  }

  @Test
  void requiresRightsAndScopesJobsToOwner() throws Exception {
    mvc.perform(
            post("/api/remote-imports")
                .header("Authorization", token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("url", url, "rightsAttested", false))))
        .andExpect(status().isUnprocessableEntity());
    String id =
        ((Map<?, ?>) remote.create(user, url, null, "web-article").get("job")).get("id").toString();
    setup();
    mvc.perform(get("/api/remote-imports/" + id).header("Authorization", token))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletionDuringFetchCannotPublishNewBook() {
    var created = remote.create(user, url, null, "web-article");
    String id = ((Map<?, ?>) created.get("job")).get("id").toString();
    when(fetcher.fetch(anyString()))
        .thenAnswer(call -> {
          db.update("UPDATE users SET status='deletion_pending' WHERE id=?", user);
          return new RemoteFetcher.Resource(
              URI.create(url),
              "text/html",
              "article.html",
              article.getBytes(StandardCharsets.UTF_8));
        });
    remote.processPending();
    assertEquals(
        0, db.queryForObject("SELECT COUNT(*) FROM books WHERE owner_id=?", Integer.class, user));
    assertEquals("failed", remote.get(user, id).get("status"));
  }

  @Test
  void rejectsPrivateAddressesMalformedTypesAndAccessChallenges() throws Exception {
    for (String address :
        List.of(
            "127.0.0.1",
            "10.0.0.1",
            "100.64.0.1",
            "169.254.169.254",
            "192.168.1.1",
            "198.18.0.1",
            "::1",
            "fc00::1",
            "64:ff9b::7f00:1"))
      assertTrue(PublicUrls.blocked(InetAddress.getByName(address)), address);
    assertFalse(PublicUrls.blocked(InetAddress.getByName("8.8.8.8")));
    assertThrows(UnknownHostException.class, () -> PublicUrls.resolve("127.0.0.1"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> PublicUrls.normalize("https://user:password@example.com/"));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> PublicUrls.normalize("http://example.com:8080/"));
    assertEquals(
        "https://example.com/article?a=1&z=2",
        PublicUrls.normalize("https://example.com/article/?z=2&utm_source=test&a=1#ignored")
            .toString());
    assertThrows(
        com.readquest.exception.ApiException.class,
        () ->
            WebDocuments.format(
                new RemoteFetcher.Resource(
                    URI.create(url), "application/pdf", "wrong.pdf", "not pdf".getBytes())));
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> WebDocuments.extract("<title>Just a moment</title>checking your browser", url));
  }
}
