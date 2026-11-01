package com.readquest.evaluation;

import java.util.List;

public final class RetrievalMetrics {
  private RetrievalMetrics() {}

  public static double reciprocalRank(List<Boolean> relevant) {
    for (int i = 0; i < relevant.size(); i++) if (relevant.get(i)) return 1.0 / (i + 1);
    return 0;
  }

  public static double percentile(List<Long> values, double quantile) {
    if (values.isEmpty()) return 0;
    var sorted = values.stream().sorted().toList();
    return sorted.get(
        Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * quantile) - 1)));
  }
}
