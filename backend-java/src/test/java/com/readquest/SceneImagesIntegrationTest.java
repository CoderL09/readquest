package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.service.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
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
class SceneImagesIntegrationTest {
  static final Path root = Path.of("target/scene-integration").toAbsolutePath();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry properties) {
    properties.add("readquest.scene-root", () -> root.toString());
    if (System.getenv("SPRING_APPLICATION_JSON") == null)
      properties.add(
          "spring.datasource.url",
          () -> "jdbc:h2:mem:scenes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired SceneImageService scenes;
  @MockitoBean SceneImageProvider provider;
  String user, token;

  @BeforeEach
  void setup() throws Exception {
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
                                "Scene Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    var auth = json.readTree(response.getResponse().getContentAsString());
    user = auth.path("user").path("id").asText();
    token = "Bearer " + auth.path("accessToken").asText();
    db.update(
        "INSERT INTO books(id,owner_id,title,status,asset_scope,created_at,updated_at)"
            + " VALUES(?,?,'Scene','completed','private',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user,
        user);
    when(provider.configured()).thenReturn(true);
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", output);
    when(provider.generate(anyString())).thenReturn(output.toByteArray());
  }

  void pro() {
    db.update(
        "INSERT INTO user_entitlements(id,source_order_no,user_id,code,label,starts_at)"
            + " VALUES(?,?,?,'pro','Test',CURRENT_TIMESTAMP)",
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        user);
  }

  String generate() throws Exception {
    var result =
        mvc.perform(
                post("/api/books/" + user + "/scene-images")
                    .header("Authorization", token)
                    .contentType("application/json")
                    .content("{\"prompt\":\"A quiet river in the morning\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("imageUrl").asText();
  }

  @Test
  void generationRequiresProAndUnavailableProvidersFailBeforeMetering() throws Exception {
    mvc.perform(
            post("/api/books/" + user + "/scene-images")
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"prompt\":\"River\"}"))
        .andExpect(status().isPaymentRequired());
    pro();
    when(provider.configured()).thenReturn(false);
    mvc.perform(
            post("/api/books/" + user + "/scene-images")
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"prompt\":\"River\"}"))
        .andExpect(status().isServiceUnavailable());
    verify(provider, never()).generate(anyString());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM feature_usage WHERE user_id=?", Integer.class, user));
  }

  @Test
  void signedImagesArePrivateBookBoundAndRevokedWithSessions() throws Exception {
    pro();
    String url = generate();
    mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
    mvc.perform(get(url.replace("/books/" + user + "/", "/books/" + UUID.randomUUID() + "/")))
        .andExpect(status().isNotFound());
    mvc.perform(get(url.substring(0, url.indexOf('?')))).andExpect(status().isNotFound());
    db.update("UPDATE refresh_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?", user);
    mvc.perform(get(url)).andExpect(status().isNotFound());
  }

  @Test
  void expiryRemovesOnlyManagedSceneAndItsRecord() throws Exception {
    pro();
    String url = generate();
    String file =
        db.queryForObject("SELECT file_name FROM scene_images WHERE user_id=?", String.class, user);
    assertTrue(Files.isRegularFile(root.resolve(file)));
    db.update(
        "UPDATE scene_images SET retention_until=TIMESTAMP '2000-01-01 00:00:00' WHERE user_id=?",
        user);
    mvc.perform(get(url)).andExpect(status().isNotFound());
    scenes.expire();
    assertFalse(Files.exists(root.resolve(file)));
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM scene_images WHERE user_id=?", Integer.class, user));
  }

  @Test
  void malformedImageRefundsUsageAndCreatesNoAsset() throws Exception {
    pro();
    when(provider.generate(anyString()))
        .thenReturn("<svg onload='bad()'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(
            post("/api/books/" + user + "/scene-images")
                .header("Authorization", token)
                .contentType("application/json")
                .content("{\"prompt\":\"River\"}"))
        .andExpect(status().isBadGateway());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM scene_images WHERE user_id=?", Integer.class, user));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM feature_usage WHERE user_id=? AND result='provider_failed'",
            Integer.class,
            user));
  }
}
