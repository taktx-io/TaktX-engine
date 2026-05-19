/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.MessageLite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtoGoldenCompatibilityTest {

  private static final Path GOLDEN_DIR = resolveGoldenDir();
  private static final boolean UPDATE_GOLDENS = Boolean.getBoolean("updateGoldens");

  @ParameterizedTest(name = "{0} matches committed golden bytes")
  @MethodSource("fixtures")
  void fixtureBytes_matchCommittedGolden(
      String resourceName, GoldenFixtureSamples.GoldenFixture<?> fixture) throws Exception {
    byte[] actual = fixture.bytes();
    Path goldenPath = GOLDEN_DIR.resolve(resourceName);

    if (UPDATE_GOLDENS) {
      Files.createDirectories(goldenPath.getParent());
      Files.write(goldenPath, actual);
    }

    assertThat(Files.exists(goldenPath)).as("golden fixture %s must exist", goldenPath).isTrue();

    byte[] expected = Files.readAllBytes(goldenPath);

    assertThat(actual)
        .as("%s byte size regression", resourceName)
        .hasSizeLessThanOrEqualTo(fixture.maxSizeBytes());
    assertThat(actual).as("%s wire bytes", resourceName).isEqualTo(expected);
    assertThat(parseFixture(fixture, expected))
        .as("%s backward parse", resourceName)
        .isEqualTo(fixture.message());
  }

  private static Stream<Arguments> fixtures() {
    return GoldenFixtureSamples.allFixtures().stream()
        .map(fixture -> Arguments.of(fixture.resourceName(), fixture));
  }

  @SuppressWarnings("unchecked")
  private static MessageLite parseFixture(
      GoldenFixtureSamples.GoldenFixture<?> fixture, byte[] bytes) throws Exception {
    return ((GoldenFixtureSamples.GoldenFixture<MessageLite>) fixture).parse(bytes);
  }

  private static Path resolveGoldenDir() {
    Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path moduleDir =
        "taktx-shared".equals(String.valueOf(workingDir.getFileName()))
            ? workingDir
            : workingDir.resolve("taktx-shared");
    return moduleDir.resolve(Path.of("src", "test", "resources", "golden"));
  }
}
