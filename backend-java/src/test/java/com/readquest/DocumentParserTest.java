package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.readquest.exception.ApiException;
import com.readquest.service.impl.DocumentParserImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DocumentParserTest {
  @TempDir Path directory;
  private final DocumentParserImpl parser = new DocumentParserImpl();

  private Path epub(Map<String, String> entries) throws Exception {
    Path file = directory.resolve(UUID.randomUUID() + ".epub");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return file;
  }

  private Map<String, String> fixture() {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("mimetype", "application/epub+zip");
    entries.put(
        "META-INF/container.xml",
        "<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile"
            + " full-path='OEBPS/book.opf'/></rootfiles></container>");
    entries.put(
        "OEBPS/book.opf",
        "<package xmlns='http://www.idpf.org/2007/opf'><manifest><item id='a' href='a.xhtml'"
            + " media-type='application/xhtml+xml'/><item id='b' href='b.xhtml'"
            + " media-type='application/xhtml+xml'/></manifest><spine><itemref idref='b'/><itemref"
            + " idref='a'/></spine></package>");
    entries.put("OEBPS/a.xhtml", "<html><body><h1>Second</h1><p>Second passage.</p></body></html>");
    entries.put(
        "OEBPS/b.xhtml",
        "<html><body><h1>First</h1><p>First passage.</p><script>alert(1)</script><img"
            + " src='https://tracker.example/x'/></body></html>");
    return entries;
  }

  @Test
  void epubUsesSpineOrderAndSanitizesHtml() throws Exception {
    var sections = parser.parse(epub(fixture()));
    assertEquals(2, sections.size());
    assertEquals("First", sections.getFirst().title());
    assertEquals("OEBPS/b.xhtml", sections.getFirst().sourceHref());
    assertFalse(sections.getFirst().html().contains("script"));
    assertFalse(sections.getFirst().html().contains("tracker.example"));
  }

  @Test
  void refusesArchiveTraversal() throws Exception {
    var entries = fixture();
    entries.put("../../outside.txt", "bad");
    assertThrows(ApiException.class, () -> parser.parse(epub(entries)));
  }

  @Test
  void preservesMetadataPicturesAndNestedNavigation() throws Exception {
    var entries = fixture();
    entries.put(
        "OEBPS/book.opf",
        entries
            .get("OEBPS/book.opf")
            .replace(
                "<manifest>",
                "<metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Real"
                    + " title</dc:title><dc:creator>A Writer</dc:creator></metadata><manifest><item"
                    + " id='nav' href='nav.xhtml' properties='nav'"
                    + " media-type='application/xhtml+xml'/><item id='cover' href='cover.png'"
                    + " properties='cover-image' media-type='image/png'/>"));
    entries.put("OEBPS/cover.png", "synthetic-image");
    entries.put(
        "OEBPS/nav.xhtml",
        "<html><body><nav epub:type='toc'><ol><li><a href='b.xhtml'>Opening</a><ol><li><a"
            + " href='a.xhtml#second'>Next</a></li></ol></li></ol></nav></body></html>");
    entries.put(
        "OEBPS/b.xhtml",
        "<html><body><p>First passage.</p><img src='cover.png' onerror='bad()'/><a"
            + " href='a.xhtml#second'>Next</a></body></html>");
    var book = parser.parseBook(epub(entries));
    assertEquals(List.of("Real title"), book.metadata().get("title"));
    assertEquals("OEBPS/cover.png", book.metadata().get("coverPath"));
    String html = book.sections().getFirst().html();
    assertTrue(html.contains("rqasset:OEBPS/cover.png"));
    assertTrue(html.contains("data-epub-href=\"OEBPS/a.xhtml#second\""));
    assertFalse(html.contains("onerror"));
    var toc = (List<?>) book.metadata().get("toc");
    assertEquals(1, toc.size());
    assertEquals(1, ((List<?>) ((Map<?, ?>) toc.getFirst()).get("children")).size());
  }

  @Test
  void reflowsWrappedPlainTextWithoutDroppingShortParagraphs() throws Exception {
    Path file = directory.resolve("wrapped.txt");
    Files.writeString(file, "We walked along\nthe river.\n\nNo.");
    assertEquals("We walked along the river.\n\nNo.", parser.parse(file).getFirst().text());
  }

  @Test
  void refusesExternalXmlEntities() throws Exception {
    var entries = fixture();
    entries.put(
        "META-INF/container.xml",
        "<!DOCTYPE x [<!ENTITY attack SYSTEM 'file:///secret'>]><container>&attack;</container>");
    assertThrows(ApiException.class, () -> parser.parse(epub(entries)));
  }

  @Test
  void refusesMissingSpineContent() throws Exception {
    var entries = fixture();
    entries.remove("OEBPS/b.xhtml");
    assertThrows(ApiException.class, () -> parser.parse(epub(entries)));
  }

  @Test
  void refusesZipBomb() throws Exception {
    var entries = fixture();
    entries.put("bomb.txt", "a".repeat(8 * 1024 * 1024 + 1));
    assertThrows(ApiException.class, () -> parser.parse(epub(entries)));
  }

  @Test
  void acceptsUtf8AndRejectsBinary() throws Exception {
    Path file = directory.resolve("book.txt");
    Files.writeString(file, "\uFEFFHello world.\r\n\r\n下一段。");
    assertTrue(parser.parse(file).getFirst().text().contains("下一段"));
    Files.write(file, new byte[] {0, 1, 2});
    assertThrows(ApiException.class, () -> parser.parse(file));
    Files.write(file, new byte[] {(byte) 0xff});
    assertThrows(ApiException.class, () -> parser.parse(file));
  }

  @Test
  void extractsActualPdfText() throws Exception {
    Path file = directory.resolve("book.pdf");
    try (var doc = new PDDocument()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText("ReadQuest PDF fixture.");
        content.endText();
      }
      doc.save(file.toFile());
    }
    assertTrue(parser.parse(file).getFirst().text().contains("ReadQuest PDF fixture."));
  }
}
