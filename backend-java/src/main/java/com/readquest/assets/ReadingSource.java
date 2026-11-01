package com.readquest.assets;

import com.readquest.exception.ApiException;
import com.readquest.pojo.Chapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.util.*;

public final class ReadingSource {
  private ReadingSource() {}

  public record Location(int start, int end, String hash) {}

  public static Location locate(Chapter chapter, String key, String sentence) {
    String text = Objects.requireNonNullElse(chapter.getContentText(), chapter.getContent());
    if (text == null || sentence == null || sentence.isBlank())
      throw new ApiException(409, "SOURCE_CHANGED", "原文来源已不可用。");
    if (key != null && key.startsWith(chapter.getId() + ":p")) {
      int cursor = 0, pi = 0;
      for (String paragraph : text.split("\\n\\s*\\n")) {
        int from = text.indexOf(paragraph, cursor);
        cursor = from + paragraph.length();
        if (paragraph.isBlank()) continue;
        var iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(paragraph);
        int begin = iterator.first(), si = 0;
        for (int end = iterator.next();
            end != BreakIterator.DONE;
            begin = end, end = iterator.next()) {
          String raw = paragraph.substring(begin, end), clean = raw.strip();
          if (clean.isBlank()) continue;
          if (key.equals(chapter.getId() + ":p" + pi + ":s" + si)) {
            if (!clean.equals(sentence.strip())) break;
            int start = from + begin + raw.length() - raw.stripLeading().length();
            return location(text, start, start + clean.length());
          }
          si++;
        }
        pi++;
      }
      throw new ApiException(409, "SOURCE_CHANGED", "原文内容已变化，无法准确定位这张卡片。");
    }
    String quote = sentence.strip();
    int start = text.indexOf(quote);
    if (start < 0 || text.indexOf(quote, start + 1) >= 0)
      throw new ApiException(409, "SOURCE_AMBIGUOUS", "无法唯一定位原文，请保留卡片上的原句作为参考。");
    return location(text, start, start + quote.length());
  }

  private static Location location(String text, int start, int end) {
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(text.getBytes(StandardCharsets.UTF_8)));
      return new Location(start, end, hash);
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
