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
class EventSubprocessTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
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
        .hasAbortedElementWithId("ServiceTask_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_02s4c8o")
        .hasPassedElementWithId("Activity_02s4c8o/Event_1krfnik", 1)
        .hasPassedElementWithId("Activity_02s4c8o/Activity_0haxijj", 1)
        .hasPassedElementWithId("Activity_02s4c8o/Event_03m37d5", 1);
  }

  @Test
  void test_EventSubProcess_ErrorTriggered_catchAll() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .waitForExternalTaskTrigger("ServiceTask_2")
        .andRespondToExternalTaskWithError("ServiceTask_2", false, "1234", "error message")
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("ServiceTask_1")
        .hasAbortedElementWithId("ServiceTask_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_0ulltxs")
        .hasPassedElementWithId("Activity_0ulltxs/Event_0gfl68s", 1)
        .hasPassedElementWithId("Activity_0ulltxs/Activity_01prqev", 1)
        .hasPassedElementWithId("Activity_0ulltxs/Event_0as9m6v", 1);
  }

  @Test
  void test_EventSubProcess_EscalationTriggered_Catchall() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .waitForExternalTaskTrigger("ServiceTask_2")
        .andRespondToExternalTaskWithEscalation(
            "ServiceTask_2", "code", "escalation message", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("ServiceTask_1")
        .hasAbortedElementWithId("ServiceTask_2")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("Activity_0w3didi")
        .hasPassedElementWithId("Activity_0w3didi/Event_1x06bbg", 1)
        .hasPassedElementWithId("Activity_0w3didi/Activity_06z7g1l", 1)
        .hasPassedElementWithId("Activity_0w3didi/Event_11ddhgq", 1);
  }

  @Test
  void test_EventSubProcess_EscalationTriggered_NonInterrupting() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .andRespondToExternalTaskWithEscalation(
            "ServiceTask_1", "1234", "error message", VariablesDTO.empty())
        .andRespondToExternalTaskWithSuccess("ServiceTask_1", VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_2")
        .andRespondToExternalTaskWithSuccess("ServiceTask_2", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("ServiceTask_1", 1)
        .hasPassedElementWithId("ServiceTask_2", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasInstantiatedElementWithId("Activity_1jz01tr")
        .hasPassedElementWithId("Activity_1jz01tr/Event_0utmfy5", 1)
        .hasPassedElementWithId("Activity_1jz01tr/Activity_0xpyuez", 1)
        .hasPassedElementWithId("Activity_1jz01tr/Event_1ffpqj3", 1);
  }

  @Test
  void test_EventSubProcess_NonInterrupting_TimerTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilIdle()
        .moveTimeForward(Duration.ofSeconds(6))
        .waitFor(Duration.ofSeconds(1))
        .waitUntilIdle()
        .waitForExternalTaskTrigger("ServiceTask_1")
        .andRespondToExternalTaskWithSuccess("ServiceTask_1", VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_2")
        .andRespondToExternalTaskWithSuccess("ServiceTask_2", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasPassedElementWithId("Gateway_1gbltwl", 1)
        .hasPassedElementWithId("ServiceTask_1", 1)
        .hasPassedElementWithId("ServiceTask_2", 1)
        .hasPassedElementWithId("Gateway_0ajyjjj", 2)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasPassedElementWithId("Non_Interrupting_Timer_Event_Subprocess")
        .hasPassedElementWithId("Non_Interrupting_Timer_Event_Subprocess/Event_0u13lv6")
        .hasPassedElementWithId("Non_Interrupting_Timer_Event_Subprocess/Activity_128spz7")
        .hasPassedElementWithId("Non_Interrupting_Timer_Event_Subprocess/Event_1g37qo8")
        .isCompleted();
  }

  @Test
  void test_EventSubProcessInSubProcess_TimerTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .waitUntilIdle()
        .moveTimeForward(Duration.ofSeconds(11))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasAbortedElementWithId("SubProcess_1/ServiceTask_1")
        .hasPassedElementWithId("SubProcess_1/SubStartEvent_1", 1)
        .hasPassedElementWithId("SubProcess_1/Timer_Event_Subprocess", 1)
        .hasPassedElementWithId("SubProcess_1/Timer_Event_Subprocess/Event_0tcrh3f", 1)
        .hasPassedElementWithId("SubProcess_1/Timer_Event_Subprocess/Activity_0g36m0j", 1)
        .hasPassedElementWithId("SubProcess_1/Timer_Event_Subprocess/Event_0w329ku", 1)
        .hasNotPassedElementWithId("SubProcess_1/EndEvent_1")
        .isCompleted();
  }

  @Test
  void test_EventSubProcessInSubProcess_MessageTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .andSendMessageWithCorrelationKey("message", "123", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasPassedElementWithId("SubProcess_1/SubStartEvent_1", 1)
        .hasPassedElementWithId("SubProcess_1/Message_Event_Subprocess", 1)
        .hasPassedElementWithId("SubProcess_1/Message_Event_Subprocess/Message_Event_1", 1)
        .hasPassedElementWithId("SubProcess_1/Message_Event_Subprocess/Activity_13ak8hs", 1)
        .hasPassedElementWithId("SubProcess_1/Message_Event_Subprocess/Event_1fhl4l4", 1)
        .hasNotPassedElementWithId("SubProcess_1/EndEvent_1");
  }

  @Test
  void test_EventSubProcess_MessageTriggered() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("ServiceTask_1", "ServiceTask_2")
        .deployProcessDefinitionAndWait("/bpmn/eventsubprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("ServiceTask_1")
        .andSendMessageWithCorrelationKey("message", "123", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasInstantiatedElementWithId("Message_Event_Subprocess")
        .hasPassedElementWithId("Message_Event_Subprocess/Message_Event_1", 1)
        .hasPassedElementWithId("Message_Event_Subprocess/Activity_13ak8hs", 1)
        .hasPassedElementWithId("Message_Event_Subprocess/Event_1fhl4l4", 1)
        .hasNotPassedElementWithId("SubProcess_1/Flow_1t7dbk1");
  }
}
