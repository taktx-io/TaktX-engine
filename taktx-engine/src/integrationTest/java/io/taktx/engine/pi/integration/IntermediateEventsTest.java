/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class IntermediateEventsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testIntermediateTimerCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/timer-intermediate-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .setTime(Instant.parse("2024-02-29T07:59:59Z"))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofMillis(1001))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("CatchEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testMessageIntermediateCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/message-intermediate-catch.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("IntermediateCatchMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "IntermediateCatchMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("MessageIntermediateCatchEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testLinkIntermediateThrowCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/link-intermediate-catch-throw.bpmn")
        .startProcessInstance(VariablesDTO.of("input", "value"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Throw_1")
        .hasInstantiatedElementWithId("Catch_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Catch_2")
        .hasVariableWithValue("input", "value")
        .hasVariableWithValue("linkOutput_1", 123)
        .hasVariableWithValue("linkOutput_2", 456);
  }
}
