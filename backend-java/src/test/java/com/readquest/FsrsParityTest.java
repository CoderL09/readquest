package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.readquest.pojo.FsrsState;
import com.readquest.service.impl.FsrsSchedulerImpl;
import java.time.Instant;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DynamicTest;

class FsrsParityTest {
  @TestFactory
  Stream<DynamicTest> matchesOriginalTsFsrsScheduler() throws Exception {
    var json = new ObjectMapper().registerModule(new JavaTimeModule());
    var scheduler = new FsrsSchedulerImpl();
    var fixture = json.readTree(getClass().getResourceAsStream("/fsrs-parity.json"));
    return StreamSupport.stream(fixture.path("cases").spliterator(), false)
        .map(test ->
            DynamicTest.dynamicTest(
                test.path("name").asText(),
                () -> {
                  var input = json.treeToValue(test.path("input"), FsrsState.class);
                  var expected = json.treeToValue(test.path("expected"), FsrsState.class);
                  var actual =
                      scheduler.schedule(
                          input,
                          Instant.parse(test.path("now").asText()),
                          test.path("verdict").asText(),
                          test.path("fuzz").asBoolean(false));
                  assertAll(
                      () -> assertEquals(expected.dueAt(), actual.dueAt()),
                      () -> assertEquals(expected.state(), actual.state()),
                      () -> assertEquals(expected.learningSteps(), actual.learningSteps()),
                      () -> assertEquals(expected.reps(), actual.reps()),
                      () -> assertEquals(expected.lapses(), actual.lapses()),
                      () -> assertEquals(expected.stability(), actual.stability(), 0.00000001),
                      () -> assertEquals(expected.difficulty(), actual.difficulty(), 0.00000001));
                }));
  }
}
