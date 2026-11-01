package io.github.eb4j.mdict;

import static org.junit.jupiter.api.Assertions.*;

import io.github.eb4j.mdict.io.MDFileInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Adler32;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class UnsignedChecksumTest {
  @TempDir Path directory;

  private Path fixture(boolean corrupt) throws Exception {
    byte[] header =
        "<Dictionary GeneratedByEngineVersion='2.0' RequiredEngineVersion='2.0' Encoding='UTF-8' Encrypted='0'/>"
            .getBytes(StandardCharsets.UTF_16LE);
    byte[] numbers = new byte[40];
    Arrays.fill(numbers, (byte) 127);
    var checksum = new Adler32();
    checksum.update(numbers);
    assertTrue(checksum.getValue() > Integer.MAX_VALUE);
    Path file = directory.resolve("fixture.mdx");
    try (var output = new DataOutputStream(Files.newOutputStream(file))) {
      output.writeInt(header.length);
      output.write(header);
      var sum = new Adler32();
      sum.update(header);
      output.writeInt(Integer.reverseBytes((int) sum.getValue()));
      output.write(numbers);
      output.writeInt((int) checksum.getValue() ^ (corrupt ? 1 : 0));
    }
    return file;
  }

  @Test
  void acceptsValidHighBitChecksum() throws Exception {
    try (var file = new MDFileInputStream(fixture(false).toString())) {
      var parser = MDictParser.createMDXParser(file);
      parser.parseHeader();
      assertDoesNotThrow(parser::parseV2Index);
    }
  }

  @Test
  void stillRejectsCorruption() throws Exception {
    try (var file = new MDFileInputStream(fixture(true).toString())) {
      var parser = MDictParser.createMDXParser(file);
      parser.parseHeader();
      assertThrows(MDException.class, parser::parseV2Index);
    }
  }
}
