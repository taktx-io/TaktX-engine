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
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.ProcessInstanceAssert;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class SignalsTest {

  private static final String WAITING_SIGNAL_CATCH_ID = "SignalCatch_1";
  private static final String COMPLETED_END_EVENT_ID = "EndEvent_1";
  private static final String SERVICE_TASK_ID = "servicetask";

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
  void testSignalBroadcastReachesAllActiveSubscriptions() throws IOException {
    BpmnTestEngine engine = SingletonBpmnTestEngine.getInstance();

    engine
        .deployProcessDefinitionAndWait("/bpmn/signal-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilIdle();

    UUID firstInstanceId =
        engine.getProcessInstanceMap().keySet().stream().findFirst().orElseThrow();

    engine.startProcessInstance(VariablesDTO.empty()).waitUntilIdle();

    UUID secondInstanceId =
        engine.getProcessInstanceMap().keySet().stream()
            .filter(processInstanceId -> !processInstanceId.equals(firstInstanceId))
            .findFirst()
            .orElseThrow();

    engine.sendSignal("123");

    Awaitility.await()
        .atMost(BpmnTestEngine.DEFAULT_DURATION)
        .untilAsserted(() -> new ProcessInstanceAssert(firstInstanceId, engine).isCompleted());
    Awaitility.await()
        .atMost(BpmnTestEngine.DEFAULT_DURATION)
        .untilAsserted(() -> new ProcessInstanceAssert(secondInstanceId, engine).isCompleted());

    new ProcessInstanceAssert(firstInstanceId, engine)
        .hasPassedElementWithId(WAITING_SIGNAL_CATCH_ID, 1)
        .hasPassedElementWithId(COMPLETED_END_EVENT_ID, 1)
        .isCompleted();
    new ProcessInstanceAssert(secondInstanceId, engine)
        .hasPassedElementWithId(WAITING_SIGNAL_CATCH_ID, 1)
        .hasPassedElementWithId(COMPLETED_END_EVENT_ID, 1)
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

  @Test
  void testSignalBoundaryInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_ID)
        .deployProcessDefinitionAndWait("/bpmn/signal_boundary.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger(SERVICE_TASK_ID)
        .sendSignal("xyz")
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("ServiceTask_1")
        .hasPassedElementWithId("Boundary_Interrupting_1")
        .hasPassedElementWithId("EndEvent_Signal_Interrupting")
        .isCompleted();
  }

  @Test
  void testSignalBoundaryNonInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds(SERVICE_TASK_ID)
        .deployProcessDefinitionAndWait("/bpmn/signal_boundary.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger(SERVICE_TASK_ID)
        .sendSignal("abc")
        .waitUntilIdle()
        .assertThatProcess()
        .hasPassedElementWithId("EndEvent_Signal_NonInterrupting")
        .hasPassedElementWithId("Boundary_NonInterrupting_1")
        .isStillActive()
        .toProcessLevel()
        .andRespondToExternalTaskWithSuccess(SERVICE_TASK_ID, VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("ServiceTask_1")
        .hasPassedElementWithId(COMPLETED_END_EVENT_ID)
        .isCompleted();
  }
}
