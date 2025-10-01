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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class EscalationsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testInterruptingEscalationTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasAbortedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "value_interrupting");
  }

  @Test
  void testInterruptingEscalation_CatchAllTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "non-matching", "escalation message", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasAbortedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "CatchAll");
  }

  @Test
  void testInterruptingEscalation_NoCode_CatchAllTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(null, null, VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasInstantiatedElementWithId("BoundaryEvent_NoReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_NonInterrupting")
        .hasAbortedElementWithId("ServiceTask_1")
        .hasVariableWithValue("MappedOutputVariable", "CatchAll");
  }

  @Test
  void testNonInterruptingEscalationTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "noninterrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "noninterrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_NonInterrupting", 2)
        .hasInstantiatedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingEscalationTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasNotPassedElementWithId("EndEvent_Noninterrupting")
        .hasAbortedElementWithId("Subprocess_1/SubServiceTask_1");
  }

  @Test
  void testNonInterruptingEscalationTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "noninterrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "noninterrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasInstantiatedElementWithId("EndEvent_Noninterrupting", 2)
        .hasPassedElementWithId("EscalationBoundaryEvent_Noninterrupting", 2);
  }

  @Test
  void testNoEscalationTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubServiceTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasNotPassedElementWithId("EndEvent_Interrupting_1")
        .hasNotPassedElementWithId("EndEvent_Noninterrupting")
        .hasNotPassedElementWithId("EscalationBoundaryEvent_Noninterrupting")
        .hasVariableWithValue("var1", "value1");
  }
}
