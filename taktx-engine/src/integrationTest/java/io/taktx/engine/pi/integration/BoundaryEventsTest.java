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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class BoundaryEventsTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testBoundaryTimerTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task-id")
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimerNotTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithSuccess("service-task-id", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_2");
  }

  @Test
  void testBoundaryTimerNonInterrupting() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-non-interrupting.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task-id")
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitFor(Duration.ofSeconds(2))
        .andRespondToExternalTaskWithSuccess("service-task-id", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Interrupted_Task_1", 3)
        .hasInstantiatedElementWithId("Boundary_Timer_1", 3);
  }

  @Test
  void testBoundaryMessageInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/boundary-message.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("BoundaryMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("BoundaryEvent_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("service-task-id")
        .hasVariableWithValue("var1", "value1");
  }

  @Test
  void testBoundaryMessageNonInterrupting() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/boundary-message-non-interrupting.bpmn")
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForExternalTaskTrigger("service-task-id")
        .waitForMessageSubscription("BoundaryEventMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .andSendMessageWithCorrelationKey(
            "BoundaryEventMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilIdle()
        .andRespondToExternalTaskWithSuccess("service-task-id", VariablesDTO.of("var2", "value2"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("service-task-id")
        .hasInstantiatedElementWithId("BoundaryEvent_1", 3)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_BoundaryEventEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("TaskDefinition", "TaskDefinition2")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("TaskDefinition")
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitForExternalTaskTrigger("TaskDefinition2")
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasAbortedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NormalEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("TaskDefinition", "TaskDefinition2")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("TaskDefinition")
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitForExternalTaskTrigger("TaskDefinition2")
        .waitUntilIdle()
        .andRespondToExternalTaskWithSuccess("TaskDefinition2", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("OkTask");
  }

  @Test
  void testBoundaryTimer_NotTriggered_Subprocess_NormalEnd() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("TaskDefinition", "TaskDefinition2")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("TaskDefinition")
        .andRespondToExternalTaskWithSuccess("TaskDefinition", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .assertThatProcess()
        .hasNotPassedElementWithId("Service_Task_2")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasInstantiatedElementWithId("OkTask")
        .hasNotPassedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }

  @Test
  void testBoundaryTimer_SubProcessTriggered_NonInterrupting_BoundaryEventEnd_NoEnd()
      throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("TaskDefinition", "TaskDefinition2")
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer-subprocess-noendevent.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("TaskDefinition")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitForExternalTaskTrigger("TaskDefinition2")
        .waitFor(Duration.ofSeconds(1))
        .andRespondToExternalTaskWithSuccess("TaskDefinition2", VariablesDTO.of("success", "true"))
        .moveTimeForward(Duration.ofMinutes(5).plusMillis(1))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasAbortedElementWithId("Subprocess_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Boundary_Timer_1")
        .hasNotPassedElementWithId("OkTask");
  }

  @Test
  void test_EventSubProcess_ErrorTriggered() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .waitForExternalTaskTrigger("ServiceTask_2")
        .andRespondToExternalTaskWithError("ServiceTask_2", false, "errorCode", "error message")
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("ServiceTask_1")
        .hasAbortedElementWithId("ServiceTask_2");
  }
}
