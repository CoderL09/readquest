package com.readquest.imports;

import com.readquest.exception.ApiException;
import com.readquest.service.RemoteFetcher;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class WebDocuments {
  private WebDocuments() {}

  public record Document(
      String title, String byline, String text, String html, String excerpt, int wordCount) {}

  public static String decode(byte[] bytes) {
    try {
      String value =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      if (value.indexOf(0) >= 0) throw new IllegalArgumentException();
      return value;
    } catch (Exception error) {
      throw new ApiException(422, "INVALID_TEXT_CONTENT", "Text must contain valid UTF-8");
    }
  }

  public static String format(RemoteFetcher.Resource input) {
    byte[] bytes = input.bytes();
    String name = input.fileName().toLowerCase(Locale.ROOT), type = input.contentType();
    boolean
        pdf =
            bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
        zip =
            bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && bytes[2] == 3
                && bytes[3] == 4;
    if (type.equals("application/pdf") || name.endsWith(".pdf")) {
      if (!pdf) throw new ApiException(422, "CONTENT_SIGNATURE_MISMATCH", "PDF signature mismatch");
      return "pdf";
    }
    if (type.equals("application/epub+zip") || name.endsWith(".epub")) {
      if (!zip)
        throw new ApiException(422, "CONTENT_SIGNATURE_MISMATCH", "EPUB signature mismatch");
      return "epub";
    }
    if (pdf) return "pdf";
    if (zip)
      throw new ApiException(422, "UNSUPPORTED_CONTENT_TYPE", "Generic archives are not supported");
    if (type.contains("html")) return "html";
    if (type.equals("text/plain")
        || name.endsWith(".txt")
        || type.equals("application/octet-stream")) {
      String sample =
          new String(Arrays.copyOf(bytes, Math.min(bytes.length, 8192)), StandardCharsets.UTF_8)
              .stripLeading();
      if (sample.matches("(?is)^(?:<!doctype\\s+html|<html\\b|<head\\b|<body\\b|<article\\b).*$"))
        return "html";
      if (type.equals("application/octet-stream") && !name.endsWith(".txt"))
        throw new ApiException(422, "UNSUPPORTED_CONTENT_TYPE", "Unknown binary content");
      return "txt";
    }
    throw new ApiException(422, "UNSUPPORTED_CONTENT_TYPE", "Source is not a readable document");
  }

  public static Document extract(String html, String source) {
    if (html.length() > 5 * 1024 * 1024)
      throw new ApiException(413, "CONTENT_TOO_LARGE", "Web page exceeds import limit");
    String sample = html.substring(0, Math.min(html.length(), 128 * 1024)).toLowerCase(Locale.ROOT);
    if (sample.contains("__cf_chl_")
        || sample.contains("cf-chl-")
        || sample.contains("<title>just a moment")
        || sample.contains("checking your browser"))
      throw new ApiException(
          422, "PAGE_ACCESS_CHALLENGE", "Source requires a browser access challenge");
    var doc = Jsoup.parse(html, source);
    var heading = doc.selectFirst("article h1");
    if (heading == null) heading = doc.selectFirst("main h1");
    if (heading == null) heading = doc.selectFirst("h1");
    String title =
        heading != null && !heading.text().isBlank()
            ? heading.text()
            : doc.title().isBlank() ? "Imported article" : doc.title();
    var author = doc.selectFirst("meta[name=author]");
    String byline = author == null ? "" : author.attr("content");
    doc.select(
            "script,style,noscript,iframe,object,embed,form,input,button,textarea,select,svg,canvas,video,audio,img,link,meta,nav,footer,aside,[role=navigation],[aria-hidden=true],[hidden]")
        .remove();
    var article = doc.selectFirst("article,main");
    if (article == null) article = doc.body();
    String safe =
        Jsoup.clean(
            article.html(),
            Safelist.basic()
                .removeTags("a")
                .addTags("h1", "h2", "h3", "h4", "h5", "h6", "section", "article"));
    var body = Jsoup.parseBodyFragment(safe);
    var parts = body.select("p,h1,h2,h3,h4,h5,h6,li,blockquote,pre");
    String text =
        parts.isEmpty()
            ? body.text()
            : String.join(
                "\n\n",
                parts.stream()
                    .filter(element -> element.select("p,li,pre,blockquote").size() <= 1)
                    .map(org.jsoup.nodes.Element::text)
                    .filter(value -> !value.isBlank())
                    .toList());
    int words = text.strip().split("\\s+").length;
    if (text.length() < 80 || words < 12)
      throw new ApiException(
          422, "PAGE_NOT_READABLE", "Source does not contain enough readable prose");
    return new Document(
        title.substring(0, Math.min(300, title.length())),
        byline.substring(0, Math.min(200, byline.length())),
        text,
        safe,
        text.substring(0, Math.min(320, text.length())).replaceAll("\\s+", " "),
        words);
  }
}
