package com.readquest.service.impl;

import com.readquest.assets.*;
import com.readquest.exception.ApiException;
import com.readquest.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BookAssetServiceImpl implements BookAssetService {
  private final BookService books;
  private final BookAssetTokens tokens;
  private final Path root;

  public BookAssetServiceImpl(
      BookService books,
      BookAssetTokens tokens,
      @Value("${readquest.storage-root:./private_uploads/books}") String root) {
    this.books = books;
    this.tokens = tokens;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public Asset read(String book, String encodedPath, String token) {
    try {
      if (encodedPath.length() > 3000) throw new IllegalArgumentException();
      String path = new String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8);
      if (!EpubPaths.safe(path)) throw new IllegalArgumentException();
      String user = tokens.verify(book, path, token);
      var source = books.requireReadable(user, book);
      if (source.getFilePath() == null || !source.getFilePath().endsWith(".epub"))
        throw new IllegalArgumentException();
      Path file = root.resolve(source.getFilePath()).normalize();
      if (!file.getParent().equals(root) || Files.isSymbolicLink(file))
        throw new IllegalArgumentException();
      try (var archive = new ZipFile(file.toFile())) {
        var entry = archive.getEntry(path);
        if (entry == null || entry.isDirectory() || entry.getSize() > 8L * 1024 * 1024)
          throw new IllegalArgumentException();
        byte[] bytes;
        try (var stream = archive.getInputStream(entry)) {
          bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
        }
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalArgumentException();
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type =
            switch (extension) {
              case "png" -> "image/png";
              case "jpg", "jpeg" -> "image/jpeg";
              case "gif" -> "image/gif";
              case "webp" -> "image/webp";
              case "woff" -> "font/woff";
              case "woff2" -> "font/woff2";
              case "ttf" -> "font/ttf";
              case "otf" -> "font/otf";
              case "svg" -> "image/svg+xml";
              default -> throw new IllegalArgumentException();
            };
        if (extension.equals("svg")) {
          String xml = new String(bytes, StandardCharsets.UTF_8);
          if (xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")
              || xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IllegalArgumentException();
          var safe =
              new Safelist()
                  .addTags(
                      "svg",
                      "g",
                      "path",
                      "rect",
                      "circle",
                      "ellipse",
                      "line",
                      "polyline",
                      "polygon",
                      "text",
                      "tspan",
                      "defs",
                      "linearGradient",
                      "radialGradient",
                      "stop",
                      "title",
                      "desc")
                  .addAttributes(
                      ":all",
                      "id",
                      "x",
                      "y",
                      "x1",
                      "x2",
                      "y1",
                      "y2",
                      "width",
                      "height",
                      "cx",
                      "cy",
                      "r",
                      "rx",
                      "ry",
                      "d",
                      "points",
                      "viewBox",
                      "viewbox",
                      "xmlns",
                      "transform",
                      "fill",
                      "stroke",
                      "stroke-width",
                      "opacity",
                      "offset",
                      "stop-color",
                      "font-size");
          bytes = Jsoup.clean(xml, safe).getBytes(StandardCharsets.UTF_8);
        }
        return new Asset(bytes, type);
      }
    } catch (Exception error) {
      throw new ApiException(404, "Book asset not found");
    }
  }
}
