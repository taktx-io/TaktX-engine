/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.integration;

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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask", "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask", "non-matching", "escalation message", VariablesDTO.of("var1", "value1"))
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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask", null, null, VariablesDTO.of("var1", "value1"))
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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "servicetask",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.of("var1", "value1"))
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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask", "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
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
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "servicetask",
            "noninterrupting",
            "escalation message",
            VariablesDTO.of("var1", "value1"))
        .andRespondToExternalTaskWithEscalation(
            "servicetask", "interrupting", "escalation message", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Interrupting")
        .hasInstantiatedElementWithId("EndEvent_Interrupting_1")
        .hasInstantiatedElementWithId("EndEvent_Noninterrupting", 2)
        .hasInstantiatedElementWithId("EscalationBoundaryEvent_Noninterrupting", 2);
  }

  @Test
  void testNoEscalationTriggeredInSubprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.of("var1", "value1"))
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

  @Test
  void testEscalationIntermediateThrowEvent() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/escalation_intermediate_throw.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("SubProcess_1/SubStartEvent_1")
        .hasPassedElementWithId("SubProcess_1/Gateway_0f2lj1i")
        .hasAbortedElementWithId("SubProcess_1/Activity_0epjhoj")
        .hasPassedElementWithId("SubProcess_1/Gateway_03xfoo0")
        .hasNotPassedElementWithId("SubProcess_1/SubEndEvent_1")
        .hasPassedElementWithId("Event_0wuhkzs")
        .hasPassedElementWithId("EndEvent_Boundary_1")
        .isCompleted();
  }
}
