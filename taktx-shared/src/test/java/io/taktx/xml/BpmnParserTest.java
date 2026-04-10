/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ParsedDefinitionsDTO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BpmnParserTest {

  /** Loads the BPMN file that already lives in the taktx-shared test resources. */
  private static String loadBpmn(String classpathPath) throws IOException {
    try (var is = BpmnParserTest.class.getResourceAsStream(classpathPath)) {
      assertThat(is).as("resource %s must exist on classpath", classpathPath).isNotNull();
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void parse_simpleProcess_populatesDefinitionKey() throws IOException {
    String xml = loadBpmn("/bpmn/task-single.bpmn");
    ParsedDefinitionsDTO result = BpmnParser.parse(xml);

    assertThat(result.getDefinitionsKey().getProcessDefinitionId()).isEqualTo("task-single");
    assertThat(result.getDefinitionsKey().getHash()).isNotBlank();
  }

  @Test
  void parse_sameXml_producesSameHash() throws IOException {
    String xml = loadBpmn("/bpmn/task-single.bpmn");
    ParsedDefinitionsDTO r1 = BpmnParser.parse(xml);
    ParsedDefinitionsDTO r2 = BpmnParser.parse(xml);
    assertThat(r1.getDefinitionsKey().getHash()).isEqualTo(r2.getDefinitionsKey().getHash());
  }

  @Test
  void parse_populatesRootProcess() throws IOException {
    String xml = loadBpmn("/bpmn/task-single.bpmn");
    ParsedDefinitionsDTO result = BpmnParser.parse(xml);
    assertThat(result.getRootProcess()).isNotNull();
    assertThat(result.getRootProcess().getId()).isEqualTo("task-single");
  }
}

