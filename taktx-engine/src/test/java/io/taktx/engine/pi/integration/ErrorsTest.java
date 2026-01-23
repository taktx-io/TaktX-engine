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
class ErrorsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testInterruptingErrorTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError("servicetask", false, "456", "message")
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_WithErrorReference")
        .hasInstantiatedElementWithId("EndEvent_Error_WithErrorReference")
        .hasAbortedElementWithId("ServiceTask_1")
        .isCompleted();
  }

  @Test
  void testInterruptingErrorTriggered_CallActivity() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "startVariable", "valueStart", "calledActivity", "calledActivityServiceTask"))
        .waitUntilChildProcessIsStarted("calledActivityServiceTask")
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError(
            "servicetask", false, "123", "message", VariablesDTO.of("err", "errtest"))
        .parentProcess()
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasAbortedElementWithId("callactivity-id")
        .hasPassedElementWithId("EndErrorEvent_123")
        .hasVariableWithValue("err", "errtest");
  }

  @Test
  void testInterruptingEscalationTriggered_CallActivity() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                "startVariable", "valueStart", "calledActivity", "calledActivityServiceTask"))
        .waitUntilChildProcessIsStarted("calledActivityServiceTask")
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithEscalation(
            "servicetask", "abc", "message", VariablesDTO.of("esc", "esctest"))
        .parentProcess()
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasAbortedElementWithId("callactivity-id")
        .hasPassedElementWithId("EndEscalationEvent_ABC")
        .hasVariableWithValue("esc", "esctest");
  }

  @Test
  void testInterruptingError_CatchAllTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError("servicetask", false, "Error_1tlo99v", "message")
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasAbortedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingError_NoCode_CatchAllTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError("servicetask", false, null, null)
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_WithErrorReference")
        .hasNotPassedElementWithId("EndEvent_Error_WithErrorReference")
        .hasInstantiatedElementWithId("BoundaryEvent_NoErrorReference")
        .hasInstantiatedElementWithId("EndEvent_NoReference")
        .hasAbortedElementWithId("ServiceTask_1");
  }

  @Test
  void testInterruptingError_noCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", false, "456", null)
        .waitUntilIdle()
        .assertThatProcess()
        .isIncident()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testInterruptingErrorTriggeredInSubprocess() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError("servicetask", false, "456", "error message")
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasInstantiatedElementWithId("BoundaryEvent_Reference")
        .hasInstantiatedElementWithId("EndEvent_Reference")
        .hasPassedElementWithId("Subprocess_1/SubStartEvent")
        .hasAbortedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasNotPassedElementWithId("Subprocess_1/SubEndEvent")
        .hasAbortedElementWithId("Subprocess_1")
        .isCompleted();
  }

  @Test
  void testCatchAllErrorTriggeredInSubprocess() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithError("servicetask", false, "Error_1tlo99v", "error message")
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_Normal")
        .hasPassedElementWithId("BoundaryEvent_NoReference")
        .hasPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference")
        .hasPassedElementWithId("Subprocess_1/SubStartEvent")
        .hasAbortedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasNotPassedElementWithId("Subprocess_1/SubEndEvent")
        .hasAbortedElementWithId("Subprocess_1")
        .isCompleted();
  }

  @Test
  void testNoErrorTriggeredInSubprocess() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/error-throw-catch_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("Subprocess_1")
        .hasPassedElementWithId("Subprocess_1/SubStartEvent")
        .hasPassedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasPassedElementWithId("Subprocess_1/SubEndEvent")
        .hasPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("StartEvent_1")
        .hasCompletedElementWithId("Subprocess_1")
        .hasCompletedElementWithId("Subprocess_1/SubServiceTask_1")
        .hasNotPassedElementWithId("BoundaryEvent_NoReference")
        .hasNotPassedElementWithId("EndEvent_NoReference")
        .hasNotPassedElementWithId("BoundaryEvent_Reference")
        .hasNotPassedElementWithId("EndEvent_Reference")
        .hasVariableWithValue("var1", "value1")
        .isCompleted();
  }

  @Test
  void testInterruptingErrorInSubprocess_noCatch() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-nested.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", false, "456", null)
        .waitUntilIdle()
        .assertThatProcess()
        .isIncident();
  }
}
