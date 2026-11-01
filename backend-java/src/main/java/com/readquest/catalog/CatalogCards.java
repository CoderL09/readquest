package com.readquest.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.readquest.exception.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import org.jsoup.Jsoup;

public final class CatalogCards {
  private CatalogCards() {}

  public static String plain(String value, int limit) {
    String text = Jsoup.parse(Objects.toString(value, "")).text().replaceAll("\\s+", " ").strip();
    return text.substring(0, Math.min(limit, text.length()));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  private static String normalized(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replace("&", " and ")
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .strip()
        .replaceAll("\\s+", " ");
  }

  public static String canonical(String title, List<String> authors, String isbn) {
    String digits = Objects.toString(isbn, "").replaceAll("[^0-9Xx]", "");
    if (digits.matches("[0-9]{13}")) {
      int sum = 0;
      for (int i = 0; i < 12; i++) sum += (digits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
      if ((10 - sum % 10) % 10 == digits.charAt(12) - '0') return "isbn13:" + digits;
    }
    return "title-author:"
        + hash(
            normalized(title)
                + "\0"
                + normalized(authors.isEmpty() ? "unknown" : authors.getFirst()));
  }

  public static String googleUrl(String input) {
    try {
      if (input == null || input.length() > 4096) return null;
      URI uri = URI.create(input);
      String host = Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT);
      if (uri.getUserInfo() != null
          || !Set.of("http", "https").contains(uri.getScheme())
          || !(host.equals("books.google.com")
              || host.equals("play.google.com")
              || host.equals("googleusercontent.com")
              || host.endsWith(".googleusercontent.com"))) return null;
      return input.replaceFirst("^http:", "https:");
    } catch (Exception error) {
      return null;
    }
  }

  private static List<String> strings(JsonNode node, int max, int length) {
    var values = new ArrayList<String>();
    if (node.isArray())
      for (var item : node) {
        if (values.size() >= max) break;
        if (item.isTextual()) {
          String value = plain(item.asText(), length);
          if (!value.isBlank()) values.add(value);
        }
      }
    return values;
  }

  private static Map<String, Object> base(
      String id, String title, List<String> authors, String isbn) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("canonicalKey", canonical(title, authors, isbn));
    result.put("title", title);
    result.put("authors", authors);
    return result;
  }

  public static List<String> googleFormats(JsonNode value) {
    var access = value.path("accessInfo");
    var result = new ArrayList<String>();
    if (!access.path("publicDomain").asBoolean()
        || !access.path("accessViewStatus").asText().equals("FULL_PUBLIC_DOMAIN")) return result;
    for (String format : List.of("epub", "pdf"))
      if (access.path(format).path("isAvailable").asBoolean()
          && googleUrl(access.path(format).path("downloadLink").asText(null)) != null)
        result.add(format);
    return result;
  }

  public static Map<String, Object> google(JsonNode volume) {
    String id = volume.path("id").asText();
    var info = volume.path("volumeInfo");
    String title = plain(info.path("title").asText(), 500);
    if (id.isBlank() || id.length() > 256 || title.isBlank())
      throw new ApiException(
          502, "INVALID_PROVIDER_RESPONSE", "Google Books returned invalid data");
    String isbn = null;
    for (var item : info.path("industryIdentifiers"))
      if (item.path("type").asText().equals("ISBN_13")) {
        isbn = item.path("identifier").asText();
        break;
      }
    var result = base("googlebooks:" + id, title, strings(info.path("authors"), 12, 200), isbn);
    var formats = googleFormats(volume);
    var access = volume.path("accessInfo");
    String preview =
        googleUrl(access.path("webReaderLink").asText(info.path("previewLink").asText(null)));
    boolean previewable =
        preview != null
            && (Set.of("SAMPLE", "FULL_PUBLIC_DOMAIN")
                    .contains(access.path("accessViewStatus").asText())
                || Set.of("PARTIAL", "ALL_PAGES").contains(access.path("viewability").asText())
                || access.path("embeddable").asBoolean());
    String cover = null;
    for (String size : List.of("large", "medium", "small", "thumbnail", "smallThumbnail")) {
      cover = googleUrl(info.path("imageLinks").path(size).asText(null));
      if (cover != null) break;
    }
    String external =
        googleUrl(
            info.path("infoLink")
                .asText(
                    info.path("canonicalVolumeLink")
                        .asText(volume.path("saleInfo").path("buyLink").asText(null))));
    if (external == null)
      external =
          "https://books.google.com/books?id="
              + java.net.URLEncoder.encode(id, StandardCharsets.UTF_8);
    result.put("coverUrl", cover);
    result.put("description", plain(info.path("description").asText(), 4000));
    result.put(
        "language",
        info.path("language").isTextual() ? plain(info.path("language").asText(), 16) : null);
    result.put("subjects", strings(info.path("categories"), 20, 120));
    String published = info.path("publishedDate").asText();
    result.put(
        "firstPublishedYear",
        published.matches("^[0-9]{4}.*") ? Integer.valueOf(published.substring(0, 4)) : null);
    result.put("providers", List.of("googlebooks"));
    result.put(
        "rating",
        info.path("averageRating").isNumber() ? info.path("averageRating").doubleValue() : null);
    result.put(
        "ratingsCount",
        info.path("ratingsCount").canConvertToLong()
            ? Math.max(0, info.path("ratingsCount").longValue())
            : null);
    var availability = new LinkedHashMap<String, Object>();
    availability.put(
        "kind", !formats.isEmpty() ? "download" : previewable ? "preview" : "external-only");
    availability.put("formats", formats);
    availability.put(
        "offerId", formats.isEmpty() ? null : "googlebooks:" + id + ":" + formats.getFirst());
    availability.put("providerId", "googlebooks");
    availability.put("externalId", id);
    result.put("availability", availability);
    var links = new LinkedHashMap<String, Object>();
    links.put("preview", previewable ? preview : null);
    links.put("external", external);
    result.put("links", links);
    return result;
  }

  public static Map<String, Object> openLibrary(JsonNode work) {
    String key = work.path("key").asText(), title = plain(work.path("title").asText(), 500);
    if (!key.matches("/works/OL[0-9]+W") || title.isBlank())
      throw new ApiException(
          502, "INVALID_PROVIDER_RESPONSE", "Open Library returned invalid data");
    String id = key.substring(key.lastIndexOf('/') + 1);
    String isbn = null;
    for (var value : work.path("isbn"))
      if (value.asText().replaceAll("\\D", "").length() == 13) {
        isbn = value.asText();
        break;
      }
    var result = base("openlibrary:" + id, title, strings(work.path("author_name"), 12, 200), isbn);
    boolean preview =
        work.path("has_fulltext").asBoolean()
            || work.path("public_scan_b").asBoolean()
            || Set.of("public", "borrowable").contains(work.path("ebook_access").asText());
    String external = "https://openlibrary.org" + key;
    result.put(
        "coverUrl",
        work.path("cover_i").canConvertToLong() && work.path("cover_i").asLong() > 0
            ? "https://covers.openlibrary.org/b/id/" + work.path("cover_i").asLong() + "-M.jpg"
            : null);
    result.put("description", "");
    result.put(
        "language",
        work.path("language").isArray() && !work.path("language").isEmpty()
            ? plain(work.path("language").get(0).asText(), 16)
            : null);
    result.put("subjects", strings(work.path("subject"), 20, 120));
    result.put(
        "firstPublishedYear",
        work.path("first_publish_year").isIntegralNumber()
            ? work.path("first_publish_year").intValue()
            : null);
    result.put("providers", List.of("openlibrary"));
    result.put(
        "rating",
        work.path("ratings_average").isNumber()
            ? work.path("ratings_average").doubleValue()
            : null);
    result.put(
        "ratingsCount",
        work.path("ratings_count").canConvertToLong()
            ? Math.max(0, work.path("ratings_count").longValue())
            : null);
    var availability = new LinkedHashMap<String, Object>();
    availability.put("kind", preview ? "preview" : "external-only");
    availability.put("formats", List.of());
    availability.put("offerId", null);
    availability.put("providerId", "openlibrary");
    availability.put("externalId", id);
    result.put("availability", availability);
    var links = new LinkedHashMap<String, Object>();
    links.put("preview", preview ? external : null);
    links.put("external", external);
    result.put("links", links);
    return result;
  }
}
