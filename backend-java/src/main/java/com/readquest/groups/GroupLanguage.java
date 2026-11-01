package com.readquest.groups;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Apache-2.0 ELD 2.0.3 scoring adaptation; see META-INF/licenses/eld.md. */
@Component
public class GroupLanguage {
  private final JsonNode model;
  private final List<Set<String>> signatures = new ArrayList<>();
  private final Set<String> singles = new HashSet<>(), phrases = new HashSet<>();
  private static final Pattern UNSAFE =
      Pattern.compile(
          "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u00AD\\u061C\\u200B\\u200C\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]");
  private static final Pattern WORD =
      Pattern.compile(
          "\\p{IsLatin}[\\p{IsLatin}\\p{M}]*(?:['’]\\p{IsLatin}[\\p{IsLatin}\\p{M}]*)*");

  public GroupLanguage(ObjectMapper json) throws Exception {
    try (var input = new ClassPathResource("data/eld-small.json").getInputStream()) {
      model = json.readTree(input);
    }
    try (var input =
        new ClassPathResource("data/group-language-signatures.json").getInputStream()) {
      var data = json.readTree(input);
      for (var row : data.path("CLEAR_NON_ENGLISH_SIGNATURES")) {
        var set = new HashSet<String>();
        row.forEach(value -> set.add(value.asText()));
        signatures.add(set);
      }
      data.path("CLEAR_SINGLE_FOREIGN_WORDS").forEach(value -> singles.add(value.asText()));
      data.path("CLEAR_SHORT_FOREIGN_EXPRESSIONS").forEach(value -> phrases.add(value.asText()));
    }
  }

  public boolean allowed(String input) {
    String text = Normalizer.normalize(input, Normalizer.Form.NFC);
    if (text.isBlank() || UNSAFE.matcher(text).find()) return false;
    if (text.codePoints()
        .anyMatch(ch ->
            Character.isLetter(ch)
                && Character.UnicodeScript.of(ch) != Character.UnicodeScript.LATIN)) return false;
    text = text.replaceAll("(?iu)\\b(?:https?://|www\\.)\\S+", " ");
    var matcher = WORD.matcher(text);
    var words = new ArrayList<String>();
    while (matcher.find())
      words.add(
          Normalizer.normalize(matcher.group(), Normalizer.Form.NFKD)
              .replaceAll("\\p{M}", "")
              .replace('’', '\'')
              .toLowerCase(Locale.ROOT));
    if (words.isEmpty()
        || phrases.contains(String.join(" ", words))
        || words.size() <= 2 && words.stream().anyMatch(singles::contains)) return false;
    for (var set : signatures) {
      long count = words.stream().filter(set::contains).distinct().count();
      if (count >= 3 && (double) count / words.size() >= .3) return false;
    }
    if (words.size() < 2) return true;
    return !detectedForeign(String.join(" ", words), words.size());
  }

  private boolean detectedForeign(String input, int wordCount) {
    String text =
        input
            .substring(0, Math.min(1000, input.length()))
            .replaceAll("[^\\p{L}]+(?<!['`’])", " ")
            .strip()
            .toLowerCase(Locale.ROOT);
    var byteWords = new ArrayList<String>();
    var encoded = new StringBuilder();
    int total = 0;
    for (int pos = 0; pos < text.length(); ) {
      int cp = text.codePointAt(pos);
      pos += Character.charCount(cp);
      if (cp < 128) {
        if (cp == 32) {
          if (!encoded.isEmpty()) {
            byteWords.add(encoded.toString());
            encoded.setLength(0);
          }
          if (total > 350) break;
        } else encoded.append((char) cp);
        total++;
      } else {
        byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) encoded.append(model.path("dictionary").get(value & 255).asText());
        total += bytes.length;
      }
      if (total > 380) break;
    }
    if (!encoded.isEmpty()) byteWords.add(encoded.toString());
    var ngrams = new LinkedHashSet<String>();
    for (String word : byteWords) {
      int length = Math.min(70, word.length()), j = 0;
      for (; j + 4 < length; j += 3) ngrams.add((j == 0 ? " " : "") + word.substring(j, j + 4));
      int start = length == 3 ? 0 : Math.max(0, length - 4);
      ngrams.add((j == 0 ? " " : "") + word.substring(start) + " ");
    }
    int count = model.path("languages").size();
    long[] raw = new long[count];
    for (String ngram : ngrams) {
      var entries = model.path("ngrams").path(ngram).fields();
      while (entries.hasNext()) {
        var entry = entries.next();
        raw[Integer.parseInt(entry.getKey())] += entry.getValue().asLong() + 53;
      }
    }
    var ranked = new ArrayList<Integer>();
    double[] scores = new double[count];
    for (int i = 0; i < count; i++)
      if (raw[i] > 0) {
        double value = (double) raw[i] / ngrams.size();
        scores[i] = value / (value + 25);
        ranked.add(i);
      }
    ranked.sort(Comparator.<Integer>comparingDouble(index -> scores[index]).reversed());
    if (ranked.isEmpty() || ngrams.size() < 3) return false;
    int best = ranked.getFirst();
    String language = model.path("languages").path(Integer.toString(best)).asText();
    if (language.isBlank() || language.equals("en")) return false;
    // Preserve ELD 2.0.3's existing reliability arithmetic for behavior compatibility.
    double next = ranked.size() > 1 ? ranked.get(1) : 0;
    if (model.path("avgScore").path(language).asDouble() * .75 > scores[best]
        || Math.abs(scores[best] - next) < .01) return false;
    double english = 0;
    for (int i = 0; i < count; i++)
      if (model.path("languages").path(Integer.toString(i)).asText().equals("en")) {
        english = scores[i];
        break;
      }
    return scores[best] - english >= (wordCount == 2 ? .3 : .08);
  }
}
