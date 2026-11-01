package com.readquest.service.impl;

import com.readquest.service.BookRetrievalService.Passage;
import java.util.*;
import java.util.regex.Pattern;

/** BM25 candidate ranking; final vector similarity remains a separate retrieval step. */
public final class PassageRanking {
  private PassageRanking() {}

  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?");
  private static final Set<String> STOP =
      Set.of(
          "what", "which", "who", "whose", "does", "did", "is", "are", "was", "were", "the", "a",
          "an", "to", "of", "in", "on", "at", "for", "with", "from", "and", "or", "as", "it", "he",
          "she", "they", "his", "her", "their", "this", "that", "these", "those", "how", "when",
          "where", "why", "do", "can", "could", "would", "should", "about", "been", "be", "have",
          "has", "had");

  private static Map<String, Integer> terms(String text) {
    var counts = new HashMap<String, Integer>();
    var words = WORD.matcher(text.toLowerCase(Locale.ROOT));
    while (words.find()) {
      String word = words.group();
      if (STOP.contains(word) || word.length() < 3) continue;
      if (word.length() > 4 && word.endsWith("s") && !word.endsWith("ss"))
        word = word.substring(0, word.length() - 1);
      counts.merge(word, 1, Integer::sum);
    }
    return counts;
  }

  public static List<Passage> rank(
      List<Passage> passages, String query, String preferred, boolean pastOnly) {
    if (passages.isEmpty()) return List.of();
    var documents = passages.stream().map(p -> terms(p.text())).toList();
    Map<String, Integer> frequency = new HashMap<>();
    var lengths =
        documents.stream()
            .mapToInt(d -> d.values().stream().mapToInt(Integer::intValue).sum())
            .toArray();
    double average = Math.max(1, Arrays.stream(lengths).average().orElse(1));
    Set<String> queryTerms = terms(query).keySet();
    for (var document : documents)
      for (String term : queryTerms)
        if (document.containsKey(term)) frequency.merge(term, 1, Integer::sum);
    var ranked = new ArrayList<Passage>();
    for (int i = 0; i < passages.size(); i++) {
      Passage p = passages.get(i);
      double score = Objects.equals(preferred, p.chapterId()) ? .25 : 0;
      for (String term : queryTerms) {
        int tf = documents.get(i).getOrDefault(term, 0), df = frequency.getOrDefault(term, 0);
        if (tf == 0) continue;
        double idf = Math.log(1 + (passages.size() - df + .5) / (df + .5));
        score += idf * tf * 2.2 / (tf + 1.2 * (.25 + .75 * lengths[i] / average));
      }
      ranked.add(
          new Passage(
              p.chapterId(),
              p.title(),
              p.text(),
              p.index(),
              score,
              p.startOffset(),
              p.endOffset(),
              p.chapterHash()));
    }
    Comparator<Passage> order = Comparator.comparingDouble(Passage::score).reversed();
    order =
        order.thenComparing(
            pastOnly
                ? Comparator.comparingInt(Passage::index).reversed()
                : Comparator.comparingInt(Passage::index));
    ranked.sort(order);
    return ranked;
  }
}
