package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.service.*;
import com.readquest.vo.AuthResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarServiceImpl implements AvatarService {
  private final UserMapper mapper;
  private final ReadingMapper users;
  private final MembershipService limits;
  private final TransactionTemplate tx;
  private final Path root;

  public AvatarServiceImpl(
      UserMapper mapper,
      ReadingMapper users,
      MembershipService limits,
      PlatformTransactionManager manager,
      @Value("${readquest.avatar-root:./private_uploads/avatars}") String root) {
    this.mapper = mapper;
    this.users = users;
    this.limits = limits;
    this.tx = new TransactionTemplate(manager);
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  private Path path(String name) {
    if (!name.matches("[a-f0-9-]{36}\\.png")) throw new ApiException(404, "Avatar not found");
    Path result = root.resolve(name).normalize();
    if (!result.getParent().equals(root) || Files.isSymbolicLink(result))
      throw new ApiException(404, "Avatar not found");
    return result;
  }

  public Resource image(String name) {
    Path file = path(name);
    if (!Files.isRegularFile(file)) throw new ApiException(404, "Avatar not found");
    return new FileSystemResource(file);
  }

  public AuthResponse.PublicUser replace(String user, MultipartFile file) {
    if (!limits.rateLimit("avatar-upload", user, 20, 3600))
      throw new ApiException(429, "Avatar upload limit reached");
    if (file.isEmpty() || file.getSize() > 2L * 1024 * 1024)
      throw new ApiException(413, "Avatar must be nonempty and 2MB or smaller");
    String name = UUID.randomUUID() + ".png";
    Path destination = path(name);
    boolean committed = false;
    try {
      byte[] bytes;
      try (var in = file.getInputStream()) {
        bytes = in.readNBytes(2 * 1024 * 1024 + 1);
      }
      if (bytes.length > 2 * 1024 * 1024) throw new ApiException(413, "Avatar exceeds 2MB");
      BufferedImage image = normalize(bytes, file.getContentType());
      Files.createDirectories(root);
      try (var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
        if (!ImageIO.write(image, "png", output)) throw new IOException();
      }
      String old =
          tx.execute(status -> {
            var account = users.lockUser(user);
            if (account == null) throw new ApiException(403, "Account is not active");
            String previous = account.getAvatarUrl();
            mapper.updateAvatar(user, "/uploads/avatars/" + name);
            return previous;
          });
      committed = true;
      if (old != null && old.startsWith("/uploads/avatars/")) {
        String previous = old.substring("/uploads/avatars/".length());
        if (previous.matches("[a-f0-9-]{36}\\.png"))
          try {
            Files.deleteIfExists(path(previous));
          } catch (IOException ignored) {
          }
      }
      return AuthResponse.PublicUser.of(mapper.findById(user));
    } catch (ApiException error) {
      throw error;
    } catch (Exception error) {
      throw new ApiException(422, "Avatar image is malformed or unsupported");
    } finally {
      if (!committed)
        try {
          Files.deleteIfExists(destination);
        } catch (IOException ignored) {
        }
    }
  }

  public static BufferedImage normalize(byte[] bytes, String mime) {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException();
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, false, false);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        String expected =
            switch (format) {
              case "jpeg", "jpg" -> "image/jpeg";
              case "png" -> "image/png";
              case "webp" -> "image/webp";
              default -> "";
            };
        if (expected.isEmpty() || !expected.equals(mime)) throw new IOException();
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 4096
            || height > 4096
            || (long) width * height > 16L * 1024 * 1024
            || reader.getNumImages(true) > 1) throw new IOException();
        if (format.equals("png") && hasPngAnimation(bytes)) throw new IOException();
        BufferedImage original = reader.read(0);
        int orientation = exifOrientation(bytes);
        int orientedWidth = orientation >= 5 ? height : width,
            orientedHeight = orientation >= 5 ? width : height;
        double scale = Math.min(1, 512.0 / Math.max(orientedWidth, orientedHeight));
        int w = Math.max(1, (int) Math.round(orientedWidth * scale)),
            h = Math.max(1, (int) Math.round(orientedHeight * scale));
        var result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var graphics = result.createGraphics();
        try {
          graphics.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          graphics.scale((double) w / orientedWidth, (double) h / orientedHeight);
          switch (orientation) {
            case 2 -> {
              graphics.translate(width, 0);
              graphics.scale(-1, 1);
            }
            case 3 -> {
              graphics.translate(width, height);
              graphics.rotate(Math.PI);
            }
            case 4 -> {
              graphics.translate(0, height);
              graphics.scale(1, -1);
            }
            case 5 -> {
              graphics.rotate(Math.PI / 2);
              graphics.scale(1, -1);
            }
            case 6 -> {
              graphics.translate(height, 0);
              graphics.rotate(Math.PI / 2);
            }
            case 7 -> {
              graphics.translate(height, width);
              graphics.rotate(Math.PI / 2);
              graphics.scale(-1, 1);
            }
            case 8 -> {
              graphics.translate(0, width);
              graphics.rotate(-Math.PI / 2);
            }
            default -> {}
          }
          graphics.drawImage(original, 0, 0, null);
        } finally {
          graphics.dispose();
        }
        return result;
      } finally {
        reader.dispose();
      }
    } catch (Exception error) {
      throw new ApiException(
          422, "Choose a valid JPEG, PNG, or WebP image up to 4096 pixels without animation");
    }
  }

  private static boolean hasPngAnimation(byte[] bytes) {
    for (int offset = 8; offset + 12 <= bytes.length; ) {
      int size = ByteBuffer.wrap(bytes, offset, 4).getInt();
      if (size < 0 || size > bytes.length - offset - 12) break;
      if (bytes[offset + 4] == 'a'
          && bytes[offset + 5] == 'c'
          && bytes[offset + 6] == 'T'
          && bytes[offset + 7] == 'L') return true;
      offset += size + 12;
    }
    return false;
  }

  static int exifOrientation(byte[] bytes) {
    // EXIF signatures are bounded by the already-enforced 2 MiB input limit.
    for (int p = 0; p + 14 < bytes.length; p++)
      if (bytes[p] == 'E'
          && bytes[p + 1] == 'x'
          && bytes[p + 2] == 'i'
          && bytes[p + 3] == 'f'
          && bytes[p + 4] == 0
          && bytes[p + 5] == 0) {
        int base = p + 6;
        try {
          ByteOrder order =
              bytes[base] == 'I' && bytes[base + 1] == 'I'
                  ? ByteOrder.LITTLE_ENDIAN
                  : ByteOrder.BIG_ENDIAN;
          var data = ByteBuffer.wrap(bytes).order(order);
          if (data.getShort(base + 2) != 42) return 1;
          long directory = (long) base + Integer.toUnsignedLong(data.getInt(base + 4));
          if (directory < base || directory + 2 > bytes.length) return 1;
          int entries = Short.toUnsignedInt(data.getShort((int) directory));
          for (int i = 0; i < entries; i++) {
            long position = directory + 2L + 12L * i;
            if (position + 12 > bytes.length) break;
            int at = (int) position;
            if (Short.toUnsignedInt(data.getShort(at)) == 0x112
                && data.getShort(at + 2) == 3
                && data.getInt(at + 4) == 1) {
              int value = Short.toUnsignedInt(data.getShort(at + 8));
              return value >= 1 && value <= 8 ? value : 1;
            }
          }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return 1;
      }
    return 1;
  }
}
