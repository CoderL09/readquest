package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.service.DocumentParser;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

@Service
public class DocumentParserImpl implements DocumentParser {
  private static final int MAX_CHARS = 4_000_000,
      MAX_ENTRY = 8 * 1024 * 1024,
      MAX_EXPANDED = 64 * 1024 * 1024;

  public List<Section> parse(Path file) {
    return parseBook(file).sections();
  }

  @Override
  public ParsedBook parseBook(Path file) {
    try {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      List<Section> out;
      Map<String, Object> metadata = Map.of();
      if (name.endsWith(".epub")) {
        var parsed = epub(file);
        out = parsed.sections();
        metadata = parsed.metadata();
      } else if (name.endsWith(".pdf")) out = pdf(file);
      else if (name.endsWith(".html")) {
        var document =
            com.readquest.imports.WebDocuments.extract(
                com.readquest.imports.WebDocuments.decode(Files.readAllBytes(file)),
                "https://import.invalid/");
        out = List.of(new Section(document.title(), document.text(), document.html(), ""));
        metadata =
            Map.of(
                "title",
                List.of(document.title()),
                "creator",
                document.byline().isBlank() ? List.of() : List.of(document.byline()),
                "sourceKind",
                "web-article");
      } else if (name.endsWith(".txt")) {
        String text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file)))
                .toString();
        if (text.indexOf(0) >= 0) throw invalid("TXT contains binary data");
        out = split(text);
      } else throw invalid("Unsupported file format");
      if (out.isEmpty() || out.stream().allMatch(s -> s.text().isBlank()))
        throw invalid("No readable text found");
      if (out.size() > 2000 || out.stream().mapToLong(s -> s.text().length()).sum() > MAX_CHARS)
        throw invalid("Document text exceeds import limits");
      return new ParsedBook(out, metadata);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw invalid("File is malformed, encrypted, or unsupported");
    }
  }

  private ApiException invalid(String message) {
    return new ApiException(422, message);
  }

  private List<Section> split(String text) {
    text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
    if (text.length() > MAX_CHARS) throw invalid("Document text exceeds import limits");
    List<Section> result = new ArrayList<>();
    StringBuilder section = new StringBuilder();
    String title = "Chapter 1";
    for (String paragraph : text.split("\\n\\s*\\n")) {
      paragraph = paragraph.replaceAll("\\n+", " ").strip();
      if (section.length() > 0 && section.length() + paragraph.length() > 12000) {
        result.add(new Section(title, section.toString().strip(), "", ""));
        section.setLength(0);
        title = "Chapter " + (result.size() + 1);
      }
      section.append(paragraph).append("\n\n");
    }
    if (!section.toString().isBlank())
      result.add(new Section(title, section.toString().strip(), "", ""));
    return result;
  }

  private List<Section> pdf(Path file) throws Exception {
    try (var doc = Loader.loadPDF(file.toFile())) {
      if (doc.isEncrypted()
          || !doc.getCurrentAccessPermission().canExtractContent()
          || doc.getNumberOfPages() > 2000) throw invalid("PDF is encrypted or too large");
      var writer =
          new StringWriter() {
            public void write(char[] c, int off, int len) {
              if (getBuffer().length() + len > MAX_CHARS)
                throw invalid("PDF text exceeds import limits");
              super.write(c, off, len);
            }

            public void write(String s) {
              if (getBuffer().length() + s.length() > MAX_CHARS)
                throw invalid("PDF text exceeds import limits");
              super.write(s);
            }
          };
      new PDFTextStripper().writeText(doc, writer);
      return split(writer.toString());
    }
  }

  private String safePath(String value) {
    if (value == null
        || value.isBlank()
        || value.contains("\\")
        || value.startsWith("/")
        || value.indexOf(0) >= 0
        || value.contains(":")) throw invalid("Unsafe EPUB path");
    Path p = Path.of(value).normalize();
    if (p.startsWith("..")) throw invalid("Unsafe EPUB path");
    return p.toString().replace('\\', '/');
  }

  private Document xml(byte[] bytes) throws Exception {
    if (bytes == null) throw invalid("EPUB manifest is missing");
    var f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
  }

  private ParsedBook epub(Path file) throws Exception {
    Map<String, byte[]> entries = new HashMap<>();
    int expanded = 0, count = 0;
    try (var input = new ZipInputStream(Files.newInputStream(file))) {
      ZipEntry e;
      while ((e = input.getNextEntry()) != null) {
        if (++count > 10000) throw invalid("Too many EPUB entries");
        String name = safePath(e.getName());
        if (e.isDirectory()) continue;
        byte[] data = input.readNBytes(MAX_ENTRY + 1);
        if (data.length > MAX_ENTRY || (expanded += data.length) > MAX_EXPANDED)
          throw invalid("EPUB expansion limit exceeded");
        if (entries.putIfAbsent(name, data) != null) throw invalid("Duplicate EPUB entry");
      }
    }
    if (!"application/epub+zip"
        .equals(
            new String(entries.getOrDefault("mimetype", new byte[0]), StandardCharsets.US_ASCII)
                .strip())) throw invalid("Not an EPUB archive");
    var roots = xml(entries.get("META-INF/container.xml")).getElementsByTagNameNS("*", "rootfile");
    if (roots.getLength() == 0) throw invalid("EPUB rootfile is missing");
    String opf = safePath(((Element) roots.item(0)).getAttribute("full-path"));
    Document packageDoc = xml(entries.get(opf));
    Map<String, Object> metadata = new LinkedHashMap<>();
    for (String tag :
        List.of(
            "title",
            "creator",
            "language",
            "publisher",
            "identifier",
            "description",
            "date",
            "rights",
            "subject")) {
      var nodes = packageDoc.getElementsByTagNameNS("*", tag);
      List<String> values = new ArrayList<>();
      for (int m = 0; m < nodes.getLength(); m++) {
        String value = nodes.item(m).getTextContent().strip();
        if (!value.isBlank()) values.add(value.substring(0, Math.min(value.length(), 16000)));
      }
      if (!values.isEmpty()) metadata.put(tag, values);
    }
    Map<String, Element> manifest = new HashMap<>();
    var items = packageDoc.getElementsByTagNameNS("*", "item");
    for (int i = 0; i < items.getLength(); i++) {
      Element e = (Element) items.item(i);
      manifest.put(e.getAttribute("id"), e);
    }
    String coverId = "";
    var metas = packageDoc.getElementsByTagNameNS("*", "meta");
    for (int m = 0; m < metas.getLength(); m++) {
      Element meta = (Element) metas.item(m);
      if ("cover".equals(meta.getAttribute("name"))) coverId = meta.getAttribute("content");
    }
    for (var entry : manifest.entrySet())
      if (entry.getKey().equals(coverId)
          || Arrays.asList(entry.getValue().getAttribute("properties").split("\\s+"))
              .contains("cover-image")) {
        String cover =
            com.readquest.assets.EpubPaths.resolve(opf, entry.getValue().getAttribute("href"));
        if (cover != null && entries.containsKey(cover)) metadata.put("coverPath", cover);
      }
    List<Map<String, Object>> toc = List.of();
    for (var entry : manifest.values())
      if (Arrays.asList(entry.getAttribute("properties").split("\\s+")).contains("nav")) {
        String navPath = com.readquest.assets.EpubPaths.resolve(opf, entry.getAttribute("href"));
        if (navPath != null && entries.containsKey(navPath)) {
          var nav =
              Jsoup.parse(new String(entries.get(navPath), StandardCharsets.UTF_8))
                  .selectFirst("nav[epub:type=toc],nav");
          if (nav != null && nav.selectFirst("ol") != null)
            toc = navToc(nav.selectFirst("ol"), navPath, 1, null, new int[] {0});
        }
      }
    if (toc.isEmpty())
      for (var entry : manifest.values())
        if ("application/x-dtbncx+xml".equals(entry.getAttribute("media-type"))) {
          String ncxPath = com.readquest.assets.EpubPaths.resolve(opf, entry.getAttribute("href"));
          if (ncxPath != null && entries.containsKey(ncxPath)) {
            var maps = xml(entries.get(ncxPath)).getElementsByTagNameNS("*", "navMap");
            if (maps.getLength() > 0)
              toc = ncxToc((Element) maps.item(0), ncxPath, 1, null, new int[] {0});
          }
        }
    metadata.put("toc", toc);
    var spine = packageDoc.getElementsByTagNameNS("*", "itemref");
    List<Section> out = new ArrayList<>();
    int chars = 0;
    for (int i = 0; i < spine.getLength(); i++) {
      Element ref = (Element) spine.item(i);
      if ("no".equals(ref.getAttribute("linear"))) continue;
      Element item = manifest.get(ref.getAttribute("idref"));
      if (item == null) throw invalid("Broken EPUB spine");
      if (!List.of("application/xhtml+xml", "text/html").contains(item.getAttribute("media-type"))
          || Arrays.asList(item.getAttribute("properties").split("\\s+")).contains("nav")) continue;
      String href = item.getAttribute("href").split("#", 2)[0];
      href = java.net.URLDecoder.decode(href.replace("+", "%2B"), StandardCharsets.UTF_8);
      Path parent = Path.of(opf).getParent();
      String resource =
          safePath(
              (parent == null ? Path.of(href) : parent.resolve(href))
                  .toString()
                  .replace('\\', '/'));
      byte[] bytes = entries.get(resource);
      if (bytes == null) throw invalid("Missing EPUB content");
      var doc = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8));
      doc.select("script,style,iframe,object,svg,form,nav,[hidden],[aria-hidden=true]").remove();
      for (var image : doc.select("img")) {
        String asset = com.readquest.assets.EpubPaths.resolve(resource, image.attr("src"));
        if (asset == null || !entries.containsKey(asset)) image.remove();
        else
          image.attr("src", "rqasset:" + asset).attr("loading", "lazy").attr("decoding", "async");
      }
      for (var link : doc.select("a[href]")) {
        String hrefValue = link.attr("href");
        String target = com.readquest.assets.EpubPaths.resolve(resource, hrefValue);
        if (target != null) {
          String fragment =
              hrefValue.contains("#") ? hrefValue.substring(hrefValue.indexOf('#')) : "";
          link.attr("data-epub-href", target + fragment)
              .attr("href", fragment.isEmpty() ? "#" : fragment);
        } else link.removeAttr("href");
      }
      StringBuilder text = new StringBuilder();
      var blocks = doc.select("p,h1,h2,h3,h4,li,blockquote,pre");
      if (blocks.isEmpty()) text.append(doc.body().text());
      else for (var p : blocks) if (!p.text().isBlank()) text.append(p.text()).append("\n\n");
      String value = text.toString().strip();
      if (value.isBlank()) continue;
      if ((chars += value.length()) > MAX_CHARS) throw invalid("EPUB text exceeds import limits");
      String title =
          doc.select("h1,h2,title").stream()
              .map(org.jsoup.nodes.Element::text)
              .filter(s -> !s.isBlank())
              .findFirst()
              .orElse("Chapter " + (out.size() + 1));
      String html =
          Jsoup.clean(
              doc.body().html(),
              Safelist.relaxed()
                  .addTags(
                      "h1",
                      "h2",
                      "h3",
                      "h4",
                      "h5",
                      "h6",
                      "figure",
                      "figcaption",
                      "section",
                      "aside",
                      "main",
                      "span")
                  .addAttributes(":all", "id", "class", "lang", "dir")
                  .addAttributes("a", "data-epub-href")
                  .addAttributes("img", "loading", "decoding")
                  .addProtocols("img", "src", "rqasset")
                  .addProtocols("a", "href", "#")
                  .preserveRelativeLinks(true));
      out.add(
          new Section(title.substring(0, Math.min(title.length(), 1000)), value, html, resource));
    }
    if (out.isEmpty()) throw invalid("EPUB has no readable spine");
    return new ParsedBook(out, metadata);
  }

  private Map<String, Object> tocItem(
      String label, String href, String source, int level, String parent, int order) {
    String path = com.readquest.assets.EpubPaths.resolve(source, href);
    String fragment = href.contains("#") ? href.substring(href.indexOf('#') + 1) : "";
    var result = new LinkedHashMap<String, Object>();
    result.put("id", "toc-" + order);
    result.put("label", label);
    result.put("hrefNoHash", Objects.toString(path, ""));
    result.put("href", Objects.toString(path, "") + (fragment.isEmpty() ? "" : "#" + fragment));
    result.put("fragment", fragment);
    result.put("level", level);
    result.put("order", order);
    if (parent != null) result.put("parentId", parent);
    return result;
  }

  private List<Map<String, Object>> navToc(
      org.jsoup.nodes.Element list, String source, int level, String parent, int[] order) {
    if (level > 16 || order[0] > 2000) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    for (var item : list.children())
      if (item.tagName().equals("li")) {
        var link =
            item.children().stream()
                .filter(node -> Set.of("a", "span").contains(node.tagName()))
                .findFirst()
                .orElse(null);
        if (link == null || link.text().isBlank()) continue;
        var node = tocItem(link.text(), link.attr("href"), source, level, parent, order[0]++);
        var children =
            item.children().stream()
                .filter(child -> child.tagName().equals("ol"))
                .findFirst()
                .orElse(null);
        node.put(
            "children",
            children == null
                ? List.of()
                : navToc(children, source, level + 1, node.get("id").toString(), order));
        result.add(node);
      }
    return result;
  }

  private List<Map<String, Object>> ncxToc(
      Element list, String source, int level, String parent, int[] order) {
    if (level > 16 || order[0] > 2000) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    for (Node child = list.getFirstChild(); child != null; child = child.getNextSibling())
      if (child instanceof Element point && "navPoint".equals(point.getLocalName())) {
        var texts = point.getElementsByTagNameNS("*", "text");
        var content = point.getElementsByTagNameNS("*", "content");
        String label =
            texts.getLength() == 0
                ? "Section " + (order[0] + 1)
                : texts.item(0).getTextContent().strip();
        String href =
            content.getLength() == 0 ? "" : ((Element) content.item(0)).getAttribute("src");
        var node = tocItem(label, href, source, level, parent, order[0]++);
        node.put("children", ncxToc(point, source, level + 1, node.get("id").toString(), order));
        result.add(node);
      }
    return result;
  }
}
