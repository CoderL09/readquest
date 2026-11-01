package com.readquest.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eb4j.mdict.MDictDictionary;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;

/** Runs in a separate bounded JVM; never starts Spring or accepts paths from HTTP clients. */
public final class DictionaryWorker {
  public static void main(String[] args) throws Exception {
    var json = new ObjectMapper();
    try {
      try (var header = new DataInputStream(Files.newInputStream(Path.of(args[0])))) {
        int size = header.readInt();
        if (size < 2 || size > 1024 * 1024) throw new IOException("Invalid header size");
        byte[] bytes = header.readNBytes(size);
        if (bytes.length != size) throw new EOFException();
        String xml = new String(bytes, StandardCharsets.UTF_16LE).toUpperCase(Locale.ROOT);
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
          throw new IOException("Unsafe header");
      }
      var dictionary = MDictDictionary.loadDictionary(args[0]);
      if (!dictionary.isMdx() || dictionary.isHeaderEncrypted())
        throw new IOException("Unsupported MDX");
      emit(json, Map.of("ready", true));
      try (var input =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = input.readLine()) != null) {
          if (line.length() > 4096) break;
          var words = json.readTree(line);
          Map<String, String> found = Map.of();
          for (var word : words) {
            String key = word.asText();
            var seen = new HashSet<String>();
            for (int links = 0; links < 8 && seen.add(key); links++) {
              var entries = dictionary.readArticles(key);
              if (entries.isEmpty()) break;
              var entry = entries.getFirst();
              String html = entry.getValue();
              if (html == null || html.isEmpty()) break;
              if (html.startsWith("@@@LINK=")) {
                key = html.substring(8).replace("\0", "").strip();
                continue;
              }
              String text = plainText(html);
              if (!text.isEmpty()) found = Map.of("headword", entry.getKey(), "definition", text);
              break;
            }
            if (!found.isEmpty()) break;
          }
          emit(json, found);
        }
      }
    } catch (Throwable error) {
      // Paths, parser diagnostics and heap dumps must not reach the API response.
      emit(json, Map.of("error", "Dictionary could not be opened or queried"));
      System.exit(2);
    }
  }

  public static String plainText(String html) {
    var doc = Jsoup.parseBodyFragment(html.substring(0, Math.min(html.length(), 250000)));
    doc.select("script,style,noscript,template,svg,img,audio,video,head").remove();
    doc.select("br").forEach(node -> node.replaceWith(new TextNode("\n")));
    doc.select("div,p,li,ul,ol,section,article,header,footer")
        .forEach(node -> node.appendChild(new TextNode("\n")));
    String text = doc.body().wholeText().replace('\u00a0', ' ').replace("\0", "");
    text =
        Arrays.stream(text.split("\\R"))
            .map(value -> value.replaceAll("[\\t ]+", " ").strip())
            .filter(value -> !value.isBlank())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    return text.substring(0, Math.min(6000, text.length()));
  }

  private static void emit(ObjectMapper json, Object value) throws IOException {
    System.out.write(
        ("RQDICT:" + json.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8));
    System.out.flush();
  }
}
