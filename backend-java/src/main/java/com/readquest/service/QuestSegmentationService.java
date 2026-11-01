package com.readquest.service;

import java.util.List;

public interface QuestSegmentationService {
  record Stage(DocumentParser.Section section, String summary) {}

  List<Stage> prepare(
      String user,
      String book,
      List<DocumentParser.Section> sections,
      String mode,
      int wordsPerQuest);
}
