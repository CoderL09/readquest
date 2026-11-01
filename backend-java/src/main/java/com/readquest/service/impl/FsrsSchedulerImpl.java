/* Numerical policy ported from ts-fsrs 5.4.1 (FSRS-6).
 * MIT license: META-INF/licenses/ts-fsrs-LICENSE.txt. */
package com.readquest.service.impl;

import com.readquest.exception.ApiException;
import com.readquest.pojo.FsrsState;
import com.readquest.service.FsrsScheduler;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class FsrsSchedulerImpl implements FsrsScheduler {
  private static final double[] W = {
    0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796, 1.4835,
    0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
  };
  private static final double DECAY = -W[20],
      FACTOR = round(Math.exp(Math.pow(DECAY, -1) * Math.log(0.9)) - 1),
      MODIFIER = round((Math.pow(0.9, 1 / DECAY) - 1) / FACTOR);

  private record Memory(double stability, double difficulty) {}

  private static double round(double v) {
    return Math.round(v * 1e8) / 1e8;
  }

  private static double clamp(double v, double min, double max) {
    return Math.min(Math.max(v, min), max);
  }

  private double initialDifficulty(int rating) {
    return round(W[4] - Math.exp((rating - 1) * W[5]) + 1);
  }

  private int elapsed(FsrsState c, Instant now) {
    return c.lastReviewedAt() == null || c.state().equals("new")
        ? 0
        : Math.toIntExact(
            ChronoUnit.DAYS.between(
                c.lastReviewedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                now.atZone(ZoneOffset.UTC).toLocalDate()));
  }

  private double recall(FsrsState c, int days) {
    return round(Math.pow(1 + FACTOR * days / c.stability(), DECAY));
  }

  public double recall(FsrsState c, Instant now) {
    return c.state().equals("new")
        ? 0
        : recall(c, (int) Math.max(0, Duration.between(c.lastReviewedAt(), now).toDays()));
  }

  private Memory memory(FsrsState c, int elapsed, int rating) {
    double s = c.stability(), d = c.difficulty();
    if (s == 0 && d == 0)
      return new Memory(Math.max(W[rating - 1], 0.1), clamp(initialDifficulty(rating), 1, 10));
    if (d < 1 || d > 10 || s < 0.001 || !Double.isFinite(s) || !Double.isFinite(d))
      throw new ApiException(422, "Invalid FSRS memory state");
    double next;
    if (elapsed == 0) {
      double factor = Math.pow(s, -W[19]) * Math.exp(W[17] * (rating - 3 + W[18]));
      if (rating >= 2) factor = Math.max(factor, 1);
      next = round(clamp(s * factor, 0.001, 36500));
    } else if (rating == 1) {
      double r = recall(c, elapsed),
          after =
              round(
                  clamp(
                      W[11]
                          * Math.pow(d, -W[12])
                          * (Math.pow(s + 1, W[13]) - 1)
                          * Math.exp((1 - r) * W[14]),
                      0.001,
                      36500));
      next = clamp(round(s / Math.exp(W[17] * W[18])), 0.001, after);
    } else {
      double r = recall(c, elapsed);
      next =
          round(
              clamp(
                  s
                      * (1
                          + Math.exp(W[8])
                              * (11 - d)
                              * Math.pow(s, -W[9])
                              * (Math.exp((1 - r) * W[10]) - 1)
                              * (rating == 2 ? W[15] : 1)),
                  0.001,
                  36500));
    }
    double damped = d + round(-W[6] * (rating - 3) * (10 - d) / 9);
    return new Memory(next, clamp(round(W[7] * initialDifficulty(4) + (1 - W[7]) * damped), 1, 10));
  }

  public FsrsState initial(Instant now) {
    return new FsrsState(
        now.truncatedTo(ChronoUnit.MILLIS).plusSeconds(600), 0, 0, 0, 0, 0, 0, 0, "new", null);
  }

  private int interval(double stability, int elapsed, boolean fuzz, String seed) {
    int value = (int) clamp(Math.round(stability * MODIFIER), 1, 365);
    if (!fuzz || value < 2.5) return value;
    double delta =
        1
            + 0.15 * Math.max(Math.min(value, 7) - 2.5, 0)
            + 0.1 * Math.max(Math.min(value, 20) - 7, 0)
            + 0.05 * Math.max(value - 20, 0);
    int min = (int) Math.max(2, Math.round(value - delta)),
        max = (int) Math.min(Math.round(value + delta), 365);
    if (value > elapsed) min = Math.max(min, elapsed + 1);
    min = Math.min(min, max);
    return (int) Math.floor(alea(seed) * (max - min + 1) + min);
  }

  public FsrsState schedule(FsrsState c, Instant now, String verdict, boolean fuzz) {
    now = now.truncatedTo(ChronoUnit.MILLIS);
    int rating =
        switch (verdict) {
          case "incorrect" -> 1;
          case "partial" -> 2;
          case "correct" -> 3;
          default -> throw new ApiException(422, "Invalid review verdict");
        };
    if (!java.util.Set.of("new", "learning", "review", "relearning").contains(c.state()))
      throw new ApiException(422, "Invalid FSRS state");
    int elapsed = elapsed(c, now);
    if (elapsed < 0) throw new ApiException(422, "Review precedes last review");
    Memory memory = memory(c, elapsed, rating);
    String seed =
        now.toEpochMilli()
            + "_"
            + (c.reps() + 1)
            + "_"
            + BigDecimal.valueOf(c.stability() * c.difficulty())
                .stripTrailingZeros()
                .toPlainString();
    int minutes = 0, days = 0, steps = 0, lapses = c.lapses();
    String state = "review";
    if (c.state().equals("review")) {
      if (rating == 1) {
        minutes = 10;
        state = "relearning";
        lapses++;
      } else {
        int hard = interval(memory(c, elapsed, 2).stability(), elapsed, fuzz, seed),
            good = interval(memory(c, elapsed, 3).stability(), elapsed, fuzz, seed);
        hard = Math.min(hard, good);
        good = Math.max(good, hard + 1);
        days = rating == 2 ? hard : good;
      }
    } else if (c.learningSteps() < 1 && rating <= 2) {
      minutes = rating == 1 ? 10 : 15;
      state = c.state().equals("relearning") ? "relearning" : "learning";
      steps = rating == 1 ? 0 : c.learningSteps();
    } else days = interval(memory.stability(), elapsed, fuzz, seed);
    days = Math.min(days, 365);
    Instant due = minutes > 0 ? now.plusSeconds(minutes * 60L) : now.plusSeconds(days * 86400L);
    return new FsrsState(
        due,
        memory.stability(),
        memory.difficulty(),
        elapsed,
        days,
        steps,
        c.reps() + 1,
        lapses,
        state,
        now);
  }

  private static class Mash {
    double n = 4022871197L;

    double value(String data) {
      for (int i = 0; i < data.length(); i++) {
        n += data.charAt(i);
        double h = 0.02519603282416938 * n;
        n = ((long) h) & 0xffffffffL;
        h -= n;
        h *= n;
        n = ((long) h) & 0xffffffffL;
        h -= n;
        n += h * 4294967296.0;
      }
      return (((long) n) & 0xffffffffL) * 0x1.0p-32;
    }
  }

  private static double alea(String seed) {
    Mash mash = new Mash();
    double s0 = mash.value(" ");
    mash.value(" ");
    mash.value(" ");
    s0 -= mash.value(seed);
    if (s0 < 0) s0 += 1;
    double t = 2091639 * s0 + 0x1.0p-32;
    return t - (int) t;
  }
}
