package com.readquest.assets;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class EpubPaths {
  private EpubPaths() {}

  public static String resolve(String source, String reference) {
    if (reference == null
        || reference.isBlank()
        || reference.indexOf(0) >= 0
        || reference.contains("\\")) return null;
    try {
      var uri = URI.create(reference.replace(" ", "%20"));
      if (uri.isAbsolute() || uri.getRawAuthority() != null || reference.startsWith("/"))
        return null;
      String relative =
          URLDecoder.decode(uri.getRawPath().replace("+", "%2B"), StandardCharsets.UTF_8);
      if (relative.contains("\\") || relative.contains(":")) return null;
      Path parent = Path.of(source).getParent();
      Path resolved =
          (relative.isEmpty()
                  ? Path.of(source)
                  : parent == null ? Path.of(relative) : parent.resolve(relative))
              .normalize();
      if (resolved.isAbsolute() || resolved.startsWith("..")) return null;
      return resolved.toString().replace('\\', '/');
    } catch (Exception error) {
      return null;
    }
  }

  public static boolean safe(String path) {
    return path != null
        && !path.isBlank()
        && !path.contains("\\")
        && !path.contains(":")
        && !path.startsWith("/")
        && !Path.of(path).isAbsolute()
        && !Path.of(path).normalize().startsWith("..");
  }
}
