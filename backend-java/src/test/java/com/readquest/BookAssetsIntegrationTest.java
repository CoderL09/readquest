package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.IngestionService;
import jakarta.servlet.http.Cookie;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookAssetsIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired IngestionService imports;
  String token, user, book;
  Cookie refresh;

  @BeforeEach
  void setup() throws Exception {
    var registered =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "displayName",
                                "EPUB Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(registered.getResponse().getContentAsString());
    token = auth.path("accessToken").asText();
    user = auth.path("user").path("id").asText();
    refresh = registered.getResponse().getCookie("rq_refresh");
    var admitted =
        imports.upload(
            user,
            new MockMultipartFile("file", "book.epub", "application/epub+zip", fixture()),
            true);
    book = admitted.book().getId();
    imports.processPending();
  }

  byte[] fixture() throws Exception {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.UTF_8));
    entries.put(
        "META-INF/container.xml",
        "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>"
            .getBytes(StandardCharsets.UTF_8));
    entries.put(
        "OPS/book.opf",
        ("<package xmlns='http://www.idpf.org/2007/opf'><metadata"
                + " xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Picture"
                + " Book</dc:title><dc:creator>A Writer</dc:creator></metadata><manifest><item id='a'"
                + " href='a.xhtml' media-type='application/xhtml+xml'/><item id='c' href='image.png'"
                + " media-type='image/png' properties='cover-image'/></manifest><spine><itemref"
                + " idref='a'/></spine></package>")
            .getBytes(StandardCharsets.UTF_8));
    entries.put(
        "OPS/a.xhtml",
        "<html><body><p>We watched the river.</p><img src='image.png' onerror='bad()'/></body></html>"
            .getBytes(StandardCharsets.UTF_8));
    entries.put("OPS/image.png", AvatarIntegrationTest.png(4, 4));
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  String cover() throws Exception {
    var result =
        mvc.perform(get("/api/books/" + book).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Picture Book"))
            .andExpect(jsonPath("$.author").value("A Writer"))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("coverUrl").asText();
  }

  @Test
  void servesSignedPrivateImagesAndSurvivesSessionRotation() throws Exception {
    String asset = cover();
    assertTrue(asset.contains("/assets/"));
    mvc.perform(get(asset))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(AvatarIntegrationTest.png(4, 4)));
    var chapters =
        mvc.perform(
                get("/api/books/" + book + "/chapters").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    String html =
        json.readTree(chapters.getResponse().getContentAsString())
            .get(0)
            .path("contentHtml")
            .asText();
    assertTrue(html.contains("/assets/"));
    assertFalse(html.contains("rqasset:"));
    assertFalse(html.contains("onerror"));
    var rotated =
        mvc.perform(post("/api/sessions/current/tokens").cookie(refresh))
            .andExpect(status().isOk())
            .andReturn();
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get(asset)).andExpect(status().isOk());
    mvc.perform(
            delete("/api/sessions/current").cookie(rotated.getResponse().getCookie("rq_refresh")))
        .andExpect(status().isNoContent());
    mvc.perform(get(asset)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnsignedTamperedAndRemovedBookAssets() throws Exception {
    String asset = cover();
    String without = asset.substring(0, asset.indexOf('?'));
    mvc.perform(get(without).param("token", "invalid")).andExpect(status().isNotFound());
    mvc.perform(get(asset.replace("/books/" + book, "/books/" + UUID.randomUUID())))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/books/" + book).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get(asset)).andExpect(status().isNotFound());
  }
}
