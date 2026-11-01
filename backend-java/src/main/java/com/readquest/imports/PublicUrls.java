package com.readquest.imports;

import com.readquest.exception.ApiException;
import java.net.*;
import java.util.*;

public final class PublicUrls {
  private PublicUrls() {}

  public static URI normalize(String input) {
    try {
      if (input == null || input.length() > 2048) throw new IllegalArgumentException();
      URI uri = URI.create(input.strip());
      String host = uri.getHost();
      String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
      if (!Set.of("http", "https").contains(scheme)
          || uri.getUserInfo() != null
          || host == null
          || uri.getPort() != -1 && uri.getPort() != (scheme.equals("https") ? 443 : 80))
        throw new IllegalArgumentException();
      host = host.toLowerCase(Locale.ROOT);
      if (host.matches(".*(?:^|\\.)(?:localhost|local|internal|home\\.arpa)\\.?$")
          || host.endsWith(".")) throw new IllegalArgumentException();
      String query = uri.getRawQuery();
      if (query != null) {
        var kept = new ArrayList<String>();
        for (String pair : query.split("&")) {
          String key =
              URLDecoder.decode(pair.split("=", 2)[0], java.nio.charset.StandardCharsets.UTF_8);
          if (!key.matches(
              "(?i)(?:utm_.+|fbclid|gclid|dclid|msclkid|mc_.+|_ga|__cf_chl_.+|cf_chl_.+)"))
            kept.add(pair);
        }
        Collections.sort(kept);
        query = String.join("&", kept);
      }
      String path = Objects.toString(uri.getRawPath(), "");
      if (path.isEmpty()) path = "/";
      else if (path.length() > 1) path = path.replaceAll("/+$", "");
      return URI.create(
          scheme + "://" + host + path + (query == null || query.isEmpty() ? "" : "?" + query));
    } catch (Exception error) {
      throw new ApiException(
          422, "URL_NOT_PUBLIC", "Only public HTTP/HTTPS URLs on standard ports are supported");
    }
  }

  public static boolean blocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return true;
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
      return a == 0
          || a == 10
          || a == 127
          || a >= 224
          || a == 100 && b >= 64 && b <= 127
          || a == 169 && b == 254
          || a == 172 && b >= 16 && b <= 31
          || a == 192 && (b == 0 || b == 168 || b == 88 && c == 99)
          || a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)
          || a == 203 && b == 0 && c == 113
          || a == 168 && b == 63 && c == 129 && (bytes[3] & 255) == 16;
    }
    if (bytes.length != 16) return true;
    int first = bytes[0] & 255, second = bytes[1] & 255;
    // Only global unicast; excludes mapped/NAT64, ULA, link-local, multicast and documentation
    // space.
    return (first & 0xe0) != 0x20
        || first == 0x20 && second == 0x01 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8;
  }

  public static InetAddress[] resolve(String host) throws UnknownHostException {
    InetAddress[] addresses = InetAddress.getAllByName(host);
    if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(PublicUrls::blocked))
      throw new UnknownHostException("Host does not resolve exclusively to public addresses");
    return addresses;
  }

  public static String display(URI uri) {
    return uri.getScheme()
        + "://"
        + uri.getRawAuthority()
        + Objects.toString(uri.getRawPath(), "/");
  }
}
