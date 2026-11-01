package com.readquest.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalMetricsTest {
  @Test
  void rankAndNearestRankPercentileHaveDefinedBoundaries() {
    assertEquals(0, RetrievalMetrics.reciprocalRank(List.of(false, false, false)));
    assertEquals(.5, RetrievalMetrics.reciprocalRank(List.of(false, true, true)));
    assertEquals(100, RetrievalMetrics.percentile(List.of(100L, 1L, 2L, 3L), .95));
    assertEquals(0, RetrievalMetrics.percentile(List.of(), .95));
  }
}
