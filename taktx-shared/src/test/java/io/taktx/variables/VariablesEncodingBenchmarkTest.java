/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.variables;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.VariablesDTO;
import io.taktx.proto.VarMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Size benchmark for the variable payload family only.
 *
 * <p>This intentionally benchmarks the legacy {@link VariablesDTO} CBOR payloads against the new
 * protobuf {@link VarMap} encoding because the PROTO-2 migration replaced only that variable tree
 * representation. The saved legacy fixtures were reproduced from the actual pre-protobuf
 * `VariablesDTO` class, which used Jackson CBOR with `@JsonFormat(shape = ARRAY)`. Other
 * top-level envelopes are already covered by the golden-wire compatibility suite from PROTO-6.1.
 *
 * <p>The benchmark records the empirically measured delta for these compact fixtures and fails only
 * if a future change makes the current protobuf encoding larger than today's baseline.
 */
class VariablesEncodingBenchmarkTest {

  @ParameterizedTest(name = "{0} benchmark")
  @MethodSource("benchmarkCases")
  void benchmark_outputsSizeComparisonToBuildLog(
      String name,
      String legacyResource,
      Map<String, Object> javaValues,
      int ignoredMaxAllowedDeltaBytes)
      throws IOException {
    byte[] legacyCbor = readResourceBytes(legacyResource);
    byte[] protoBytes = encodeProto(javaValues);

    System.out.printf(
        "Variable size benchmark [%s]: legacy-cbor=%dB proto-varmap=%dB delta=%+dB%n",
        name, legacyCbor.length, protoBytes.length, protoBytes.length - legacyCbor.length);

    assertThat(legacyCbor).as("legacy fixture for %s", name).isNotEmpty();
    assertThat(protoBytes).as("proto bytes for %s", name).isNotEmpty();
  }

  @ParameterizedTest(name = "{0} legacy fixture is committed")
  @MethodSource("benchmarkCases")
  void benchmark_usesSavedLegacyCborBytes(
      String name,
      String legacyResource,
      Map<String, Object> ignored,
      int ignoredMaxAllowedDeltaBytes)
      throws IOException {
    assertThat(readResourceBytes(legacyResource))
        .as("saved legacy CBOR fixture for %s", name)
        .isNotEmpty();
  }

  @ParameterizedTest(name = "{0} stays within current regression budget")
  @MethodSource("benchmarkCases")
  void benchmark_preventsFurtherSizeRegression(
      String name, String legacyResource, Map<String, Object> javaValues, int maxAllowedDeltaBytes)
      throws IOException {
    byte[] legacyCbor = readResourceBytes(legacyResource);
    byte[] protoBytes = encodeProto(javaValues);

    assertThat(protoBytes.length - legacyCbor.length)
        .as(
            "proto variable payload for %s grew beyond the documented %dB delta baseline",
            name, maxAllowedDeltaBytes)
        .isLessThanOrEqualTo(maxAllowedDeltaBytes);
  }

  private static Stream<Arguments> benchmarkCases() {
    return Stream.of(
        Arguments.of(
            "five-numeric-variables",
            "legacy-cbor/variables-5-numeric.cbor",
            orderedMap(
                "amount", 100L,
                "retryCount", 3L,
                "priority", 1L,
                "lineItems", 5L,
                "score", 42L),
            25),
        Arguments.of(
            "five-string-variables",
            "legacy-cbor/variables-5-string.cbor",
            orderedMap(
                "customer", "Alice",
                "region", "EMEA",
                "status", "APPROVED",
                "channel", "PORTAL",
                "currency", "EUR"),
            26),
        Arguments.of(
            "nested-object-variable",
            "legacy-cbor/variables-nested-object.cbor",
            orderedMap("payload", orderedMap("name", "Alice", "amount", 100L, "active", true)),
            19),
        Arguments.of(
            "list-variable",
            "legacy-cbor/variables-list.cbor",
            orderedMap("items", List.of(1L, 2L, 3L, "four", true)),
            17));
  }

  private static byte[] encodeProto(Map<String, Object> javaValues) {
    VariablesDTO dto = VariablesDTO.ofObjectMap(javaValues);
    return Variables.toVarMap(dto.getVariables()).toByteArray();
  }

  private static byte[] readResourceBytes(String resourceName) throws IOException {
    try (InputStream input =
        VariablesEncodingBenchmarkTest.class.getClassLoader().getResourceAsStream(resourceName)) {
      assertThat(input).as("resource %s", resourceName).isNotNull();
      return input.readAllBytes();
    }
  }

  private static Map<String, Object> orderedMap(Object... keyValues) {
    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put((String) keyValues[i], keyValues[i + 1]);
    }
    return values;
  }
}

