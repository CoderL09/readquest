package com.readquest.service;

import java.util.List;

public interface BookRetrievalService {
  record Passage(
      String chapterId,
      String title,
      String text,
      int index,
      double score,
      int startOffset,
      int endOffset,
      String chapterHash) {
    public Passage(String chapterId, String title, String text, int index, double score) {
      this(chapterId, title, text, index, score, 0, text.length(), "");
    }
  }

  record Result(List<Passage> passages, String method) {}

  boolean configured();

  Result retrieve(String user, String book, String query, String chapter, boolean pastOnly);
}
