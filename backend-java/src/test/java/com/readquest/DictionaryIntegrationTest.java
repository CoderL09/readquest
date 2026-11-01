package com.readquest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.dictionary.*;
import com.readquest.service.impl.DictionaryServiceImpl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Adler32;
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
class DictionaryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String token;

  @BeforeEach
  void account() throws Exception {
    var response =
        mvc.perform(
                post("/api/users")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                UUID.randomUUID() + "@example.test",
                                "password",
                                "Password123!",
                                "displayName",
                                "Dictionary Reader"))))
            .andExpect(status().isCreated())
            .andReturn();
    token =
        "Bearer "
            + json.readTree(response.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
  }

  @Test
  void dictionaryIsPrivateReplaceableAndDeletionIsScoped() throws Exception {
    mvc.perform(get("/api/users/me/dictionary").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dictionary").isEmpty());
    var upload =
        new MockMultipartFile(
            "file",
            "Sample.mdx",
            "application/octet-stream",
            fixture("<p>河岸 A river bank.</p><script>alert(1)</script>"));
    mvc.perform(
            multipart("/api/users/me/dictionary")
                .file(upload)
                .with(req -> {
                  req.setMethod("PUT");
                  return req;
                })
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dictionary.displayName").value("Sample"))
        .andExpect(jsonPath("$.dictionary.storageKey").doesNotExist());
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", "banks")
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.definition.headword").value("bank"))
        .andExpect(jsonPath("$.definition.definition").value("河岸 A river bank."));
    String first = token;
    account();
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", "bank")
                .header("Authorization", token))
        .andExpect(jsonPath("$.definition").isEmpty());
    mvc.perform(delete("/api/users/me/dictionary").header("Authorization", token))
        .andExpect(status().isNotFound());
    token = first;
    var bad =
        new MockMultipartFile(
            "file", "bad.mdx", "application/octet-stream", new byte[] {0, 0, 0, 99});
    mvc.perform(
            multipart("/api/users/me/dictionary")
                .file(bad)
                .with(req -> {
                  req.setMethod("PUT");
                  return req;
                })
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", "bank")
                .header("Authorization", token))
        .andExpect(jsonPath("$.definition.definition").value("河岸 A river bank."));
    mvc.perform(
            multipart("/api/users/me/dictionary")
                .file(
                    new MockMultipartFile(
                        "file",
                        "New.mdx",
                        "application/octet-stream",
                        fixture("<p>New definition.</p>")))
                .with(req -> {
                  req.setMethod("PUT");
                  return req;
                })
                .header("Authorization", token))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", "bank")
                .header("Authorization", token))
        .andExpect(jsonPath("$.definition.definition").value("New definition."));
    mvc.perform(delete("/api/users/me/dictionary").header("Authorization", token))
        .andExpect(status().isNoContent());
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", "bank")
                .header("Authorization", token))
        .andExpect(jsonPath("$.definition").isEmpty());
  }

  @Test
  void validatesWordAndFileType() throws Exception {
    mvc.perform(
            get("/api/users/me/dictionary/entries")
                .param("word", " ")
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            multipart("/api/users/me/dictionary")
                .file(new MockMultipartFile("file", "bad.txt", "text/plain", new byte[] {1}))
                .with(req -> {
                  req.setMethod("PUT");
                  return req;
                })
                .header("Authorization", token))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void inflectionsAndMarkupAreSafe() {
    assertEquals(List.of("studies", "study"), DictionaryServiceImpl.candidates("STUDIES"));
    assertEquals(List.of("running", "run"), DictionaryServiceImpl.candidates("running"));
    assertEquals(
        "Hello\nWorld",
        DictionaryWorker.plainText(
            "<p>Hello</p><style>bad</style><p>World</p><img src=x onerror=bad()>"));
  }

  static byte[] fixture(String definition) throws Exception {
    // Synthetic MDX 2.0 with one uncompressed key and record; contains no external dictionary data.
    byte[] header =
        ("<Dictionary GeneratedByEngineVersion=\"2.0\" RequiredEngineVersion=\"2.0\""
                + " Encoding=\"UTF-8\" Format=\"Html\" Encrypted=\"0\" KeyCaseSensitive=\"No\""
                + " Title=\"Test\"/>")
            .getBytes(StandardCharsets.UTF_16LE);
    var keys = new ByteArrayOutputStream();
    var k = new DataOutputStream(keys);
    k.writeLong(0);
    k.write("bank\0".getBytes(StandardCharsets.UTF_8));
    byte[] keyBlock = block(keys.toByteArray()),
        record = block((definition + "\0").getBytes(StandardCharsets.UTF_8));
    var index = new ByteArrayOutputStream();
    var i = new DataOutputStream(index);
    i.writeLong(1);
    i.writeShort(4);
    i.write("bank\0".getBytes());
    i.writeShort(4);
    i.write("bank\0".getBytes());
    i.writeLong(keyBlock.length);
    i.writeLong(keys.size());
    byte[] keyIndex = block(index.toByteArray());
    var kh = new ByteArrayOutputStream();
    var h = new DataOutputStream(kh);
    h.writeLong(1);
    h.writeLong(1);
    h.writeLong(index.size());
    h.writeLong(keyIndex.length);
    h.writeLong(keyBlock.length);
    var result = new ByteArrayOutputStream();
    var out = new DataOutputStream(result);
    out.writeInt(header.length);
    out.write(header);
    out.writeInt(Integer.reverseBytes((int) adler(header)));
    out.write(kh.toByteArray());
    out.writeInt((int) adler(kh.toByteArray()));
    out.write(keyIndex);
    out.write(keyBlock);
    out.writeLong(1);
    out.writeLong(1);
    out.writeLong(16);
    out.writeLong(record.length);
    out.writeLong(record.length);
    out.writeLong((definition + "\0").getBytes(StandardCharsets.UTF_8).length);
    out.write(record);
    return result.toByteArray();
  }

  static long adler(byte[] bytes) {
    var checksum = new Adler32();
    checksum.update(bytes);
    return checksum.getValue();
  }

  static byte[] block(byte[] bytes) throws Exception {
    var result = new ByteArrayOutputStream();
    var out = new DataOutputStream(result);
    out.writeInt(0);
    out.writeInt((int) adler(bytes));
    out.write(bytes);
    return result.toByteArray();
  }
}
