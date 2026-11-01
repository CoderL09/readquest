package com.readquest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.readquest.groups.GroupLanguage;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;

class GroupLanguageParityTest {
  @TestFactory
  Stream<DynamicTest> originalLanguageExamples() throws Exception {
    var json = new ObjectMapper();
    var detector = new GroupLanguage(json);
    try (var input = getClass().getResourceAsStream("/group-language-parity.json")) {
      var fixtures = json.readTree(input);
      return java.util.stream.StreamSupport.stream(fixtures.spliterator(), false)
          .map(value ->
              DynamicTest.dynamicTest(
                  value.path("text").asText(),
                  () ->
                      assertEquals(
                          value.path("allowed").asBoolean(),
                          detector.allowed(value.path("text").asText()))));
    }
  }
}
