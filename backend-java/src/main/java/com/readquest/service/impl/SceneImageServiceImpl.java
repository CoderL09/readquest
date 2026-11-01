package com.readquest.service.impl;

import com.readquest.assets.BookAssetTokens;
import com.readquest.exception.ApiException;
import com.readquest.mapper.SceneImageMapper;
import com.readquest.pojo.SceneImage;
import com.readquest.service.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SceneImageServiceImpl implements SceneImageService {
  private final SceneImageProvider provider;
  private final BookService books;
  private final MembershipService membership;
  private final SceneImageMapper mapper;
  private final AccountWriteGuard guard;
  private final BookAssetTokens tokens;
  private final Path root;
  private final int dailyImages;
  private final Set<String> generating = ConcurrentHashMap.newKeySet();

  public SceneImageServiceImpl(
      SceneImageProvider provider,
      BookService books,
      MembershipService membership,
      SceneImageMapper mapper,
      AccountWriteGuard guard,
      BookAssetTokens tokens,
      @Value("${SCENE_DAILY_IMAGE_LIMIT:2}") int dailyImages,
      @Value("${readquest.scene-root:./private_uploads/scenes}") String root) {
    this.provider = provider;
    this.books = books;
    this.membership = membership;
    this.mapper = mapper;
    this.guard = guard;
    this.tokens = tokens;
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.dailyImages = Math.max(0, dailyImages);
  }

  private Path path(String name) {
    if (name == null || !name.matches("[a-f0-9-]{36}\\.png"))
      throw new ApiException(404, "Scene image not found");
    Path file = root.resolve(name).normalize();
    if (!file.getParent().equals(root) || Files.isSymbolicLink(root) || Files.isSymbolicLink(file))
      throw new ApiException(404, "Scene image not found");
    return file;
  }

  public String generate(String user, String book, String prompt) {
    books.requireReadable(user, book);
    if (membership.active(user).stream().noneMatch(e -> "pro".equals(e.getCode())))
      throw new ApiException(402, "ENTITLEMENT_REQUIRED", "ReadQuest Pro is required");
    if (!provider.configured()) throw new ApiException(503, "Image generation is not configured");
    if (!membership.rateLimit("scene-user-daily", user, dailyImages, 86400)
        || !membership.rateLimit("scene-global", "all", 30, 3600))
      throw new ApiException(429, "Image rate limit reached");
    if (!generating.add(user)) throw new ApiException(429, "Image generation is already running");
    String operation = UUID.randomUUID().toString(),
        id = UUID.randomUUID().toString(),
        name = id + ".png";
    boolean admitted = false, committed = false, fileCreated = false;
    try {
      if (!membership.admit(user, "scene_image", operation))
        throw new ApiException(429, "QUOTA_EXCEEDED", "Daily AI quota exceeded");
      admitted = true;
      byte[] bytes = normalize(provider.generate(prompt));
      String url = tokens.url(user, book, "scene:" + id);
      url =
          url.substring(0, url.indexOf("/assets/"))
              + "/scene-images/"
              + id
              + url.substring(url.indexOf("?token="));
      Files.createDirectories(root);
      Files.write(path(name), bytes, StandardOpenOption.CREATE_NEW);
      fileCreated = true;
      guard.execute(
          user,
          () -> {
            books.requireReadable(user, book);
            mapper.insert(
                new SceneImage(
                    id, user, book, name, Instant.now().plusSeconds(30L * 86400), Instant.now()));
            return null;
          });
      committed = true;
      return url;
    } catch (ApiException error) {
      if (admitted) membership.providerFailed(user, operation);
      throw error;
    } catch (Exception error) {
      if (admitted) membership.providerFailed(user, operation);
      throw new ApiException(502, "Scene image could not be saved");
    } finally {
      generating.remove(user);
      if (fileCreated && !committed)
        try {
          Files.deleteIfExists(path(name));
        } catch (Exception ignored) {
        }
    }
  }

  static byte[] normalize(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > 8 * 1024 * 1024)
      throw new ApiException(502, "Image exceeds size limit");
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IOException();
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (!Set.of("png", "jpeg", "jpg", "webp").contains(format)
            || width <= 0
            || height <= 0
            || width > 4096
            || height > 4096
            || (long) width * height > 16L * 1024 * 1024
            || reader.getNumImages(true) > 1) throw new IOException();
        var image = reader.read(0);
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output) || output.size() > 8 * 1024 * 1024)
          throw new IOException();
        return output.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (Exception error) {
      throw new ApiException(502, "Image provider returned a malformed image");
    }
  }

  public byte[] read(String book, String id, String token) {
    String user = tokens.verify(book, "scene:" + id, token);
    books.requireReadable(user, book);
    var image = mapper.find(id);
    if (image == null
        || !image.userId().equals(user)
        || !image.bookId().equals(book)
        || !image.retentionUntil().isAfter(Instant.now()))
      throw new ApiException(404, "Scene image not found");
    try (var stream = Files.newInputStream(path(image.fileName()))) {
      byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
      if (bytes.length > 8 * 1024 * 1024) throw new IOException();
      return bytes;
    } catch (IOException error) {
      throw new ApiException(404, "Scene image not found");
    }
  }

  private void delete(SceneImage image) {
    try {
      Files.deleteIfExists(path(image.fileName()));
      mapper.delete(image.id());
    } catch (IOException error) {
      throw new UncheckedIOException(error);
    }
  }

  public void deleteOwned(String user) {
    for (; ; ) {
      var rows = mapper.owned(user);
      if (rows.isEmpty()) return;
      rows.forEach(this::delete);
    }
  }

  @Scheduled(fixedDelay = 60000)
  public void expire() {
    for (var image : mapper.expired())
      try {
        delete(image);
      } catch (RuntimeException ignored) {
      }
  }
}
