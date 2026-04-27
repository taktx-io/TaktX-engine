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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class GatewayTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testParallelGateway() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/gateway-parallel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasInstantiatedElementWithId("EndEvent_1", 1);
  }

  @Test
  void testInclusiveGateway_DefaultFlow() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(1)))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_3")
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2");
  }

  @Test
  void testInclusiveGateway_SingleFlow() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(2)))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", new DummyObject(3)))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 3, "inputVariable2", "a"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep2() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 2, "inputVariable3", "a"))
        .waitUntilDone()
        .assertThatProcess()
        .hasNotPassedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("Task_3");
  }

  @Test
  void testInclusiveGateway_MultipleFlows_Deep3() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/inclusive-gateway-deep.bpmn")
        .startProcessInstance(
            VariablesDTO.of("inputVariable", 2, "inputVariable2", "a", "inputVariable3", "a"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("Task_3");
  }

  @Test
  void testExclusiveGatewy() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/sequence-flow-condition.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 1))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasNotPassedElementWithId("Task_2")
        .hasInstantiatedElementWithId("EndEvent_1")

        // now test the alternative default flow
        .toProcessLevel()
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_2")
        .hasNotPassedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testEventBasedGatewy_TimerTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/event-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 1))
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMillis(5001))
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasPassedElementWithId("Gateway_0wn8ufc")
        .hasPassedElementWithId("Timer_Event")
        .hasPassedElementWithId("Timer_End_Event")
        .hasAbortedElementWithId("Signal_Event")
        .hasAbortedElementWithId("Message_Event");
  }

  @Test
  void testEventBasedGatewy_SignalTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/event-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", 1))
        .waitUntilIdle()
        .sendSignal("fgh")
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasPassedElementWithId("Gateway_0wn8ufc")
        .hasPassedElementWithId("Signal_Event")
        .hasPassedElementWithId("Signal_End_Event")
        .hasAbortedElementWithId("Timer_Event")
        .hasAbortedElementWithId("Message_Event");
  }

  @Test
  void testEventBasedGatewy_MessageTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/event-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", "1"))
        .waitUntilIdle()
        .andSendMessageWithCorrelationKey("Msg", "1", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasPassedElementWithId("Gateway_0wn8ufc")
        .hasPassedElementWithId("Message_Event")
        .hasPassedElementWithId("Message_End_Event")
        .hasAbortedElementWithId("Timer_Event")
        .hasAbortedElementWithId("Signal_Event");
  }

  private static class DummyObject {

    private final int i;

    public DummyObject(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }
  }
}
