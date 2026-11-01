package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.readquest.assets.ReadingSource;
import com.readquest.exception.ApiException;
import com.readquest.pojo.Chapter;
import org.junit.jupiter.api.Test;

class ReadingSourceTest {
  Chapter chapter(String text) {
    var c = new Chapter();
    c.setId("chapter");
    c.setContentText(text);
    return c;
  }

  @Test
  void locatesRepeatedSentencesByTheirStableKey() {
    String text = "  Same 😀 sentence.\n\nSame 😀 sentence.";
    var result = ReadingSource.locate(chapter(text), "chapter:p1:s0", "Same 😀 sentence.");
    assertEquals(text.lastIndexOf("Same"), result.start());
    assertEquals("Same 😀 sentence.", text.substring(result.start(), result.end()));
    assertEquals(64, result.hash().length());
  }

  @Test
  void rejectsChangedOrAmbiguousSourcesRatherThanGuessing() {
    assertThrows(
        ApiException.class,
        () ->
            ReadingSource.locate(chapter("Different sentence."), "chapter:p0:s0", "Old sentence."));
    assertThrows(
        ApiException.class,
        () -> ReadingSource.locate(chapter("Same.\n\nSame."), "legacy", "Same."));
    assertEquals(
        2,
        ReadingSource.locate(chapter("  Unique sentence."), "legacy", "Unique sentence.").start());
  }
}
