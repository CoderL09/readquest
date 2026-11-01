package com.readquest.service;

import java.nio.file.Path;
import java.util.List;

public interface DocumentParser {
  record Section(String title, String text, String html, String sourceHref) {}

  record ParsedBook(List<Section> sections, java.util.Map<String, Object> metadata) {}

  List<Section> parse(Path file);

  default ParsedBook parseBook(Path file) {
    return new ParsedBook(parse(file), java.util.Map.of());
  }
}
