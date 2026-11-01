package com.readquest.service.impl;

import com.fasterxml.jackson.databind.*;
import com.readquest.dto.CalibrationResponseInput;
import com.readquest.exception.ApiException;
import com.readquest.mapper.*;
import com.readquest.pojo.*;
import com.readquest.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileServiceImpl implements ProfileService {
  private final ProfileMapper mapper;
  private final ReadingMapper users;
  private final BookService books;
  private final ObjectMapper json;
  private final List<Sample> definitions;
  private final List<BookMetadata> seeds;
  private final List<Band> bands;

  public record Sample(String id, int lexile, String title, String text) {}

  public record Band(String label, int min, int max) {}

  public ProfileServiceImpl(ProfileMapper m, ReadingMapper u, BookService b, ObjectMapper j)
      throws java.io.IOException {
    mapper = m;
    users = u;
    books = b;
    json = j;
    definitions = Arrays.asList(read("/data/calibration.json", Sample[].class));
    seeds = Arrays.asList(read("/data/metadata-seeds.json", BookMetadata[].class));
    bands = Arrays.asList(read("/data/lexile-bands.json", Band[].class));
  }

  private <T> T read(String resource, Class<T> type) throws java.io.IOException {
    try (var input = getClass().getResourceAsStream(resource)) {
      return json.readValue(Objects.requireNonNull(input), type);
    }
  }

  private Instant now() {
    return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  private void active(String user) {
    if (users.lockUser(user) == null) throw new ApiException(403, "Account is not active");
  }

  private ReadingProfile profile(String user) {
    mapper.ensure(user);
    return mapper.profile(user);
  }

  private int clamp(int v, int min, int max) {
    return Math.min(max, Math.max(min, v));
  }

  private double clamp(double v, double min, double max) {
    return Math.min(max, Math.max(min, v));
  }

  private double round(double v, int decimals) {
    double f = Math.pow(10, decimals);
    return Math.round(v * f) / f;
  }

  private long number(Map<String, Object> m, String key) {
    Object value = m.get(key);
    return value instanceof Number n ? n.longValue() : 0;
  }

  private void band(ReadingProfile p, int lexile) {
    p.setCurrentLexile(lexile);
    p.setComfortZoneMin(clamp(lexile - 120, 100, 1300));
    p.setComfortZoneMax(clamp(lexile + 40, 180, 1400));
    p.setIPlusOneMin(clamp(lexile + 50, 220, 1450));
    p.setIPlusOneMax(clamp(lexile + 150, 300, 1500));
  }

  private void seed() {
    if (mapper.metadataCount() != 0) return;
    for (BookMetadata book : seeds) {
      book.setId("seed-" + book.getLexile());
      mapper.insertMetadata(book);
    }
  }

  private Map<String, Object> bookMetadata(BookMetadata m) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId());
    out.put("_id", m.getId());
    out.put("title", m.getTitle());
    out.put("author", m.getAuthor());
    out.put("cover", m.getCover());
    out.put("description", m.getDescription());
    out.put("lexile", m.getLexile());
    out.put("difficultyLevel", m.getDifficultyLevel());
    out.put("genre", m.getGenre());
    out.put("sourceUrl", m.getSourceUrl());
    out.put("downloadStatus", m.getDownloadStatus());
    out.put("linkedBookId", m.getLinkedBookId());
    out.put("createdAt", m.getCreatedAt());
    return out;
  }

  private double score(BookMetadata b, ReadingProfile p) {
    double ideal = (p.getIPlusOneMin() + p.getIPlusOneMax()) / 2.0;
    return Math.abs(b.getLexile() - ideal)
        + (b.getLexile() >= p.getIPlusOneMin() && b.getLexile() <= p.getIPlusOneMax() ? -120 : 0)
        + (b.getLexile() < p.getComfortZoneMin() ? 280 : 0)
        + (b.getLexile() > p.getIPlusOneMax() + 150 ? 420 : 0);
  }

  @Transactional
  public Map<String, Object> library(String user) {
    active(user);
    seed();
    ReadingProfile p = profile(user);
    List<BookMetadata> all = mapper.metadata();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("profile", p);
    result.put(
        "recommended",
        all.stream()
            .min(Comparator.comparingDouble(b -> score(b, p)))
            .map(this::bookMetadata)
            .orElse(null));
    List<Map<String, Object>> grouped = new ArrayList<>();
    for (Band band : bands) {
      List<Map<String, Object>> selected =
          all.stream()
              .filter(b -> {
                Band first =
                    bands.stream()
                        .filter(v -> b.getLexile() >= v.min() && b.getLexile() <= v.max())
                        .findFirst()
                        .orElse(bands.getLast());
                return first.label().equals(band.label());
              })
              .map(this::bookMetadata)
              .toList();
      grouped.add(
          Map.of("label", band.label(), "min", band.min(), "max", band.max(), "books", selected));
    }
    result.put("bands", grouped);
    return result;
  }

  @Transactional
  public Map<String, Object> metadata(String user, String id) {
    active(user);
    BookMetadata book = mapper.metadataById(id);
    if (book == null) throw new ApiException(404, "Book metadata not found");
    return Map.of("book", bookMetadata(book));
  }

  @Transactional
  public Map<String, Object> passport(String user) {
    active(user);
    seed();
    ReadingProfile p = profile(user);
    List<BookMetadata> all = mapper.metadata();
    return Map.of(
        "profile",
        p,
        "comfortableBooks",
        all.stream()
            .filter(b ->
                b.getLexile() >= p.getComfortZoneMin() && b.getLexile() <= p.getComfortZoneMax())
            .limit(3)
            .map(this::bookMetadata)
            .toList(),
        "challengeBooks",
        all.stream()
            .filter(b -> b.getLexile() >= p.getIPlusOneMin() && b.getLexile() <= p.getIPlusOneMax())
            .limit(3)
            .map(this::bookMetadata)
            .toList(),
        "estimatedJourney",
        Map.of(
            "dailyMinutes",
            10,
            "timeframe",
            "4–6 weeks",
            "milestone",
            p.getCurrentLexile() < 760
                ? "a longer chapter book with fewer pauses"
                : "denser literary or nonfiction chapters"));
  }

  private Sample choose(int target, Set<String> used) {
    return definitions.stream()
        .filter(s -> !used.contains(s.id()))
        .min(Comparator.comparingInt(s -> Math.abs(s.lexile() - target)))
        .orElseThrow(() -> new ApiException(409, "No calibration sample available"));
  }

  private CalibrationSample sample(CalibrationSession session, Sample definition) {
    CalibrationSample s = new CalibrationSample();
    s.setSessionId(session.getId());
    s.setStep(session.getCurrentStep());
    s.setSampleId(definition.id());
    s.setLexile(definition.lexile());
    s.setText(definition.text());
    s.setStartedAt(now());
    return s;
  }

  private Map<String, Object> calibrationView(CalibrationSession s) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sessionId", s.getId());
    out.put("completed", s.getStatus().equals("completed"));
    if (s.getStatus().equals("completed")) {
      try {
        out.put("profile", json.readTree(s.getFinalProfileJson()));
      } catch (Exception e) {
        throw new IllegalStateException("Stored calibration profile is invalid");
      }
      return out;
    }
    CalibrationSample active = mapper.samples(s.getId()).getLast();
    Sample definition =
        definitions.stream()
            .filter(d -> d.id().equals(active.getSampleId()))
            .findFirst()
            .orElseThrow();
    out.put("currentStep", s.getCurrentStep());
    out.put("totalSteps", 3);
    out.put("estimatedLexile", s.getEstimatedLexile());
    out.put(
        "sample",
        new Sample(active.getSampleId(), active.getLexile(), definition.title(), active.getText()));
    return out;
  }

  @Transactional
  public Map<String, Object> startCalibration(String user) {
    active(user);
    CalibrationSession s = new CalibrationSession();
    s.setId(UUID.randomUUID().toString());
    s.setUserId(user);
    s.setStatus("active");
    s.setCurrentStep(1);
    s.setEstimatedLexile(500);
    s.setCreatedAt(now());
    s.setUpdatedAt(s.getCreatedAt());
    mapper.insertCalibration(s);
    mapper.insertSample(sample(s, choose(500, Set.of())));
    return calibrationView(s);
  }

  @Transactional
  public Map<String, Object> calibration(String user, String id) {
    active(user);
    CalibrationSession s = mapper.calibration(user, id);
    if (s == null) throw new ApiException(404, "Calibration session not found");
    return calibrationView(s);
  }

  private double score(CalibrationSample s) {
    return clamp(
        s.getComprehensionScore() * 0.58
            + s.getFlowScore() * 0.34
            - clamp(s.getLookupCount() * 0.08, 0, 0.32)
            - (s.getReadingSeconds() > 80 ? 0.08 : 0),
        0,
        1);
  }

  @Transactional
  public Map<String, Object> respond(
      String user, String id, int step, CalibrationResponseInput in) {
    active(user);
    if (!Double.isFinite(in.readingSeconds())
        || !Double.isFinite(in.lookupCount())
        || !Double.isFinite(in.comprehensionScore())
        || !Double.isFinite(in.flowScore()))
      throw new ApiException(422, "Invalid calibration values");
    CalibrationSession s = mapper.calibration(user, id);
    if (s == null) throw new ApiException(404, "Calibration session not found");
    List<CalibrationSample> samples = mapper.samples(id);
    CalibrationSample sample =
        samples.stream()
            .filter(v -> v.getStep() == step)
            .findFirst()
            .orElseThrow(() -> new ApiException(409, "Calibration step is not current"));
    int seconds = clamp((int) Math.round(in.readingSeconds()), 1, 900),
        lookups = clamp((int) Math.round(in.lookupCount()), 0, 50);
    double comprehension = clamp(in.comprehensionScore(), 0, 1), flow = clamp(in.flowScore(), 0, 1);
    if (sample.getFinishedAt() != null) {
      if (sample.getReadingSeconds() != seconds
          || sample.getLookupCount() != lookups
          || Double.compare(sample.getComprehensionScore(), comprehension) != 0
          || Double.compare(sample.getFlowScore(), flow) != 0)
        throw new ApiException(409, "Calibration response already saved with different values");
      return calibrationView(s);
    }
    if (!s.getStatus().equals("active") || step != s.getCurrentStep())
      throw new ApiException(409, "Calibration session is not active");
    sample.setReadingSeconds(seconds);
    sample.setLookupCount(lookups);
    sample.setComprehensionScore(comprehension);
    sample.setFlowScore(flow);
    sample.setFinishedAt(now());
    mapper.saveSample(sample);
    double scored = score(sample);
    int delta = scored >= 0.82 ? 90 : scored >= 0.68 ? 45 : scored >= 0.52 ? 10 : -80;
    s.setEstimatedLexile(clamp(s.getEstimatedLexile() + delta, 280, 1050));
    if (step >= 3) {
      ReadingProfile p = profile(user);
      double sum = 0, weights = 0;
      for (CalibrationSample v : samples) {
        double w = clamp(score(v) + 0.2, 0.35, 1.05);
        sum += v.getLexile() * w;
        weights += w;
      }
      int lexile = (int) Math.round(clamp(sum / Math.max(weights, 1), 250, 1100) / 10) * 10;
      band(p, lexile);
      p.setAverageLookupPerSentence(
          round(
              samples.stream().mapToInt(CalibrationSample::getLookupCount).average().orElse(0), 2));
      p.setAverageComprehensionScore(
          round(
              samples.stream()
                  .mapToDouble(CalibrationSample::getComprehensionScore)
                  .average()
                  .orElse(0),
              2));
      mapper.save(p);
      s.setStatus("completed");
      try {
        s.setFinalProfileJson(json.writeValueAsString(p));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    } else {
      Set<String> used = new HashSet<>();
      for (CalibrationSample v : samples) used.add(v.getSampleId());
      s.setCurrentStep(step + 1);
      mapper.insertSample(sample(s, choose(s.getEstimatedLexile(), used)));
    }
    mapper.saveCalibration(s);
    return calibrationView(s);
  }

  private record Signals(
      long completed, double average, double quiet, double accuracy, long attempts) {}

  private List<Map<String, Object>> support(String user, ReadingProgress latest) {
    Map<Integer, long[]> counts = new TreeMap<>();
    for (LookupItem l :
        mapper.flowItems(
            user,
            latest == null ? null : latest.getBookId(),
            latest == null ? null : latest.getChapterId())) {
      long[] c = counts.computeIfAbsent(l.getParagraphIndex(), k -> new long[4]);
      c[0]++;
      if (l.getType().equals("word")) c[1]++;
      if (l.getType().equals("phrase")) c[2]++;
      if (Set.of("grammar", "sentencePattern").contains(l.getType())) c[3]++;
    }
    List<Map<String, Object>> out = new ArrayList<>();
    counts.forEach((index, c) ->
        out.add(
            Map.of(
                "paragraphIndex",
                index,
                "supportCount",
                c[0],
                "wordCount",
                c[1],
                "phraseCount",
                c[2],
                "grammarCount",
                c[3])));
    return out;
  }

  private Signals signals(
      ReadingProgress latest, List<Map<String, Object>> support, Map<String, Object> reviews) {
    long completed = latest == null ? 0 : latest.getCompletedParagraphs(),
        sum = support.stream().mapToLong(v -> number(v, "supportCount")).sum(),
        supported = support.stream().filter(v -> number(v, "paragraphIndex") < completed).count(),
        attempts = number(reviews, "attempts");
    return new Signals(
        completed,
        completed > 0 ? round(sum / (double) completed, 1) : 0,
        completed > 0 ? round(Math.max(0, completed - supported) / (double) completed, 2) : 0,
        attempts > 0 ? round(number(reviews, "correct") / (double) attempts, 2) : 0,
        attempts);
  }

  private Map<String, Object> adjustment(ReadingProfile profile, Signals s) {
    boolean reading = s.completed() >= 5, reviews = s.attempts() >= 3;
    String direction = "stable",
        reason = "Keep reading a little more before changing the recommendation range.";
    int delta = 0;
    if (reading
        && s.average() <= 0.8
        && s.quiet() >= 0.45
        && (reviews ? s.accuracy() >= 0.78 : true)) {
      direction = "up";
      delta = 20;
      reason =
          "Reading flow is smooth and support requests are low, so the i+1 range can gently move"
              + " up.";
    } else if ((reading && s.average() >= 2.5 && s.quiet() <= 0.2)
        || (reviews && s.accuracy() <= 0.55)) {
      direction = "down";
      delta = -20;
      reason =
          "The current material is interrupting reading too often, so the next recommendation"
              + " should be gentler.";
    } else if (reading)
      reason = "The current difficulty looks appropriate. Keep the recommendation range stable.";
    if (delta != 0
        && profile.getLastAdjustedAt() != null
        && Duration.between(profile.getLastAdjustedAt(), now()).toMillis() < 6 * 3600000L) {
      direction = "stable";
      delta = 0;
      reason =
          "The recommendation range was adjusted recently, so the system is waiting for more"
              + " reading evidence before moving it again.";
    }
    double confidence =
        round(
            clamp(
                0.35
                    + Math.min(s.completed(), 12) * 0.025
                    + Math.min(s.attempts(), 8) * 0.025
                    + (s.quiet() >= 0.4 ? 0.1 : 0),
                0,
                1),
            2);
    return Map.of(
        "direction",
        direction,
        "lexileDelta",
        delta,
        "confidence",
        confidence,
        "reason",
        reason,
        "signals",
        Map.of(
            "completedParagraphs",
            s.completed(),
            "averageSupportPerParagraph",
            s.average(),
            "quietRatio",
            s.quiet(),
            "reviewAccuracy",
            s.accuracy(),
            "reviewAttempts",
            s.attempts()));
  }

  @Transactional
  public void adjustAfterProgress(String user) {
    active(user);
    ReadingProfile p = profile(user);
    List<ReadingProgress> recent = mapper.recentProgress(user);
    ReadingProgress latest = recent.isEmpty() ? null : recent.getFirst();
    var a = adjustment(p, signals(latest, support(user, latest), mapper.reviewStats(user)));
    int delta = ((Number) a.get("lexileDelta")).intValue();
    if (delta != 0) {
      band(p, clamp((int) Math.round((p.getCurrentLexile() + delta) / 10.0) * 10, 200, 1300));
      p.setLastAdjustedAt(now());
    }
    p.setReadingConfidence(((Number) a.get("confidence")).doubleValue());
    p.setLastAdjustmentReason((String) a.get("reason"));
    p.setTotalReadingMinutes(
        mapper.bookProgress(user).stream().mapToLong(BookProgress::getTotalReadingMs).sum()
            / 60000.0);
    mapper.save(p);
  }

  @Transactional
  public Map<String, Object> growth(String user) {
    active(user);
    ReadingProfile p = profile(user);
    List<ReadingProgress> recent = mapper.recentProgress(user);
    List<BookProgress> progress = mapper.bookProgress(user);
    p.setTotalReadingMinutes(
        progress.stream().mapToLong(BookProgress::getTotalReadingMs).sum() / 60000.0);
    mapper.save(p);
    ReadingProgress latest = recent.isEmpty() ? null : recent.getFirst();
    List<Map<String, Object>> support = support(user, latest);
    Map<String, Object> reviews = mapper.reviewStats(user), lookups = mapper.lookupStats(user);
    long attempts = number(reviews, "attempts"), correct = number(reviews, "correct");
    double accuracy = attempts == 0 ? 0 : Math.round(correct * 100.0 / attempts);
    List<Map<String, Object>> grouped =
        progress.stream()
            .map(v ->
                Map.<String, Object>of(
                    "bookId",
                    v.getBookId(),
                    "completedParagraphs",
                    v.getCompletedParagraphs(),
                    "totalParagraphs",
                    v.getTotalParagraphs(),
                    "totalReadingMs",
                    v.getTotalReadingMs(),
                    "updatedAt",
                    v.getUpdatedAt(),
                    "progress",
                    v.getTotalParagraphs() == 0
                        ? 0
                        : Math.min(
                            100,
                            Math.round(
                                v.getCompletedParagraphs() * 100.0 / v.getTotalParagraphs()))))
            .toList();
    Map<String, Object> activeBook = null;
    if (latest != null) {
      try {
        Book b = books.requireReadable(user, latest.getBookId());
        activeBook =
            Map.of(
                "id",
                b.getId(),
                "title",
                b.getTitle(),
                "author",
                b.getAuthor(),
                "coverUrl",
                Objects.requireNonNullElse(b.getCoverUrl(), ""),
                "progress",
                latest.getTotalParagraphs() == 0
                    ? 0
                    : Math.round(
                        latest.getCompletedParagraphs() * 100.0 / latest.getTotalParagraphs()),
                "totalChapters",
                b.getTotalChapters(),
                "completedParagraphs",
                latest.getCompletedParagraphs(),
                "totalParagraphs",
                latest.getTotalParagraphs());
      } catch (ApiException e) {
        if (e.status() != 404) throw e;
      }
    }
    List<Map<String, Object>> recentView =
        recent.stream()
            .map(v ->
                Map.<String, Object>of(
                    "id",
                    v.getBookId() + ":" + v.getChapterId(),
                    "bookId",
                    v.getBookId(),
                    "chapterId",
                    v.getChapterId(),
                    "currentParagraphIndex",
                    v.getCurrentParagraphIndex(),
                    "completedParagraphs",
                    v.getCompletedParagraphs(),
                    "totalParagraphs",
                    v.getTotalParagraphs(),
                    "totalReadingMs",
                    v.getTotalReadingMs(),
                    "updatedAt",
                    v.getUpdatedAt()))
            .toList();
    Signals signal = signals(latest, support, reviews);
    List<Map<String, Object>> last =
        support.subList(Math.max(0, support.size() - 5), support.size());
    int half = last.size() / 2;
    double
        first =
            last.subList(0, half).stream()
                .mapToLong(v -> number(v, "supportCount"))
                .average()
                .orElse(0),
        second =
            last.subList(half, last.size()).stream()
                .mapToLong(v -> number(v, "supportCount"))
                .average()
                .orElse(0);
    String trend =
        last.size() < 3
            ? "notEnoughData"
            : second < first ? "down" : second > first ? "up" : "stable";
    Map<String, Object> flow =
        Map.of(
            "averageSupportPerParagraph",
            signal.average(),
            "totalSupport",
            support.stream().mapToLong(v -> number(v, "supportCount")).sum(),
            "quietParagraphs",
            Math.max(
                0,
                signal.completed()
                    - support.stream()
                        .filter(v -> number(v, "paragraphIndex") < signal.completed())
                        .count()),
            "supportTrend",
            trend,
            "recentParagraphs",
            last);
    Map<String, Object> reading = new LinkedHashMap<>();
    reading.put("totalReadingMinutes", p.getTotalReadingMinutes());
    reading.put(
        "completedBooks",
        grouped.stream().filter(v -> ((Number) v.get("progress")).intValue() >= 100).count());
    reading.put("activeBook", activeBook);
    reading.put("bookProgress", grouped);
    reading.put("recentProgress", recentView);
    List<String> feedback = new ArrayList<>();
    if (activeBook != null) feedback.add("你正在把《" + activeBook.get("title") + "》一点点读亮。");
    if (p.getTotalReadingMinutes() > 0)
      feedback.add("你已经沉淀了 " + Math.round(p.getTotalReadingMinutes()) + " 分钟真实阅读时间。");
    feedback.add("当前推荐挑战区间是 " + p.getIPlusOneMin() + "L–" + p.getIPlusOneMax() + "L，目标是刚好有一点难。");
    return Map.of(
        "profile",
        p,
        "lookup",
        lookups,
        "review",
        Map.of("attempts", attempts, "correct", correct, "accuracy", accuracy),
        "reading",
        reading,
        "flow",
        flow,
        "adjustment",
        adjustment(p, signal),
        "feedback",
        feedback.stream().limit(4).toList());
  }
}
