/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class SignalsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testSignalCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/signal-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForSignalSubscription("123")
        .sendSignal("456")
        .waitFor(Duration.ofSeconds(3))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .sendSignal("123")
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted();
  }

  @Test
  void testSignalThrow() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/signal-throw.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilDone()
        .waitForSignal("xyz")
        .waitForSignal("abc")
        .assertThatProcess()
        .isCompleted();
  }

  @Test
  void testSignalStart() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/signal-start.bpmn")
        .sendSignal("fgh")
        .waitForNewProcessInstance()
        .assertThatProcess()
        .isCompleted();
  }
}
