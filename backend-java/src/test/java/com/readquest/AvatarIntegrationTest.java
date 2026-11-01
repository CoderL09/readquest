package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readquest.service.impl.AvatarServiceImpl;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
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
class AvatarIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String token;

  @BeforeEach
  void account() throws Exception {
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
                                "Avatar Reader",
                                "password",
                                "Password123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    token =
        "Bearer "
            + json.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
  }

  static byte[] png(int w, int h) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
    return out.toByteArray();
  }

  @Test
  void replacesPublicNormalizedImageAndRemovesPrevious() throws Exception {
    String previous = null;
    for (int count = 0; count < 2; count++) {
      var result =
          mvc.perform(
                  multipart("/api/users/me/avatar")
                      .file(
                          new MockMultipartFile("avatar", "image.png", "image/png", png(800, 400)))
                      .with(req -> {
                        req.setMethod("PUT");
                        return req;
                      })
                      .header("Authorization", token))
              .andExpect(status().isOk())
              .andReturn();
      String url =
          json.readTree(result.getResponse().getContentAsString())
              .path("user")
              .path("avatarUrl")
              .asText();
      var image =
          mvc.perform(get(url))
              .andExpect(status().isOk())
              .andExpect(content().contentType("image/png"))
              .andReturn();
      var decoded =
          ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
      assertEquals(512, decoded.getWidth());
      assertEquals(256, decoded.getHeight());
      mvc.perform(get("/api/users/me").header("Authorization", token))
          .andExpect(jsonPath("$.avatarUrl").value(url));
      if (previous != null) mvc.perform(get(previous)).andExpect(status().isNotFound());
      previous = url;
    }
  }

  @Test
  void rejectsMismatchedMimeAndOversizedDimensions() throws Exception {
    mvc.perform(
            multipart("/api/users/me/avatar")
                .file(new MockMultipartFile("avatar", "image.jpg", "image/jpeg", png(8, 8)))
                .with(req -> {
                  req.setMethod("PUT");
                  return req;
                })
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity());
    assertThrows(
        com.readquest.exception.ApiException.class,
        () -> AvatarServiceImpl.normalize(png(4097, 1), "image/png"));
    mvc.perform(get("/uploads/avatars/not-an-image.png")).andExpect(status().isNotFound());
  }
}
