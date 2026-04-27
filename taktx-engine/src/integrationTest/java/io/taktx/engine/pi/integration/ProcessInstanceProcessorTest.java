/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.ProcessDefinitionDTO;
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
class ProcessInstanceProcessorTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testDeploy() throws IOException {
    ProcessDefinitionDTO processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();

    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(1);

    processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single-2.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);

    processDefinition =
        SingletonBpmnTestEngine.getInstance()
            .deployProcessDefinition("/bpmn/task-single-2.bpmn")
            .waitForProcessDeployment()
            .deployedProcessDefinition();
    assertThat(processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId())
        .isEqualTo("task-single");
    assertThat(processDefinition.getDefinitions().getRootProcess().getFlowElements().values())
        .hasSize(5);
    assertThat(processDefinition.getVersion()).isEqualTo(2);
  }

  @Test
  void testSubProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("SubProcess_1")
        .hasPassedElementWithId("SubProcess_1/SubStartEvent_1")
        .hasPassedElementWithId("SubProcess_1/SubTask_1")
        .hasPassedElementWithId("SubProcess_1/SubEndEvent_1")
        .hasPassedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessServiceTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask")
        .andRespondToExternalTaskWithSuccess("servicetask", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1")
        .hasPassedElementWithId("SubProcess_1")
        .hasPassedElementWithId("EndEvent_1")
        .hasPassedElementWithId("SubProcess_1/SubStartEvent_1")
        .hasPassedElementWithId("SubProcess_1/SubTask_1")
        .hasPassedElementWithId("SubProcess_1/SubEndEvent_1");
  }

  @Test
  void testSubProcessTaskNested() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-nested.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithSuccess("service-task", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessCallActivitySingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(
            VariablesDTO.of("startVariable", "valueStart", "calledActivity", "calledActivity"))
        .waitUntilChildProcessIsCompleted("calledActivity")
        .assertThatProcess()
        .hasInstantiatedElementWithId("calledActivity:StartEvent_CalledElement")
        .hasInstantiatedElementWithId("calledActivity:task_CalledElement")
        .hasInstantiatedElementWithId("calledActivity:EndEvent_CalledElement")
        .toProcessLevel()
        .assertThatParentProcess()
        .hasInstantiatedElementWithId("task-callactivity:StartEvent_1")
        .toProcessLevel()
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("task-callactivity:callactivity-id")
        .hasInstantiatedElementWithId("task-callactivity:EndEvent_1");
  }

  @Test
  void testScheduledStart_R5() throws IOException {
    // All executed timers fall within the same time bucket
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r5.bpmn")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(2001))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilDone();
  }

  @Test
  void testScheduledStart_R60() throws IOException {
    // The last timer falls into the next time bucket
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r60.bpmn")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(20001))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance(Duration.ofMillis(60000))
        .waitUntilDone()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance()
        .waitUntilDone();
  }

  @Test
  void testTerminateSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsActive("SubTask_1")
        .abortProcessInstance()
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("SubProcess_1")
        .hasAbortedElementWithId("SubProcess_1/SubTask_1")
        .isAborted();
  }

  @Test
  void testTerminateChildSingleCallActivityInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-callactivity.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilChildProcessIsStarted("calledActivityServiceTask")
        .parentProcess()
        .abortProcessInstance()
        .waitUntilChildProcessIsTerminated("calledActivityServiceTask")
        .assertThatProcess()
        .isAborted()
        .toProcessLevel()
        .parentProcess()
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("SubProcess_1")
        .isAborted();
  }

  @Test
  void testTerminateEndEvent() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask_a", "servicetask_b")
        .deployProcessDefinitionAndWait("/bpmn/terminate_end_event.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask_a")
        .waitForExternalTaskTrigger("servicetask_b")
        .andRespondToExternalTaskWithSuccess("servicetask_b", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("activity_a")
        .hasPassedElementWithId("activity_b")
        .hasPassedElementWithId("Terminate_End_Event")
        .hasNotPassedElementWithId("EndEvent_1")
        .isAborted();
  }

  @Test
  void testTerminateEndEvent_Subprocess() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask_a", "servicetask_b")
        .deployProcessDefinitionAndWait("/bpmn/terminate_end_event_subprocess.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("servicetask_a")
        .waitForExternalTaskTrigger("servicetask_b")
        .andRespondToExternalTaskWithSuccess("servicetask_b", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("Event_0tscatf")
        .hasAbortedElementWithId("Activity_0xvkaul")
        .hasAbortedElementWithId("Activity_0xvkaul/activity_a")
        .hasPassedElementWithId("Activity_0xvkaul/activity_b")
        .hasPassedElementWithId("Activity_0xvkaul/Terminate_End_Event")
        .hasNotPassedElementWithId("Event_0ycspwm")
        .isCompleted();
  }

  @Test
  void testTerminateSpecificSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsWaiting("SubTask_1")
        .terminateElementInstance()
        .waitUntilDone()
        .assertThatProcess()
        .hasAbortedElementWithId("SubProcess_1")
        .hasAbortedElementWithId("SubProcess_1/SubTask_1")
        .isCompleted();
  }

  @Test
  void testMessageStartEvent() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/message_start.bpmn")
        .waitForMessageSubscription("StartMessage2")
        .sendMessage("StartMessage2", VariablesDTO.of("var1", "value1"))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .toProcessLevel()
        .sendMessage("StartMessage2", VariablesDTO.of("var2", "value2"))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var2", "value2")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/message_start_2.bpmn")
        .waitForMessageSubscription("StartMessage3")
        .sendMessage("StartMessage3", VariablesDTO.of("var3", "value3"))
        .waitForNewProcessInstance()
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_2")
        .hasInstantiatedElementWithId("EndEvent_2")
        .hasVariableWithValue("var3", "value3")
        .toProcessLevel();
  }

  @Test
  void testReceiveTask() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/receive-task.bpmn")
        .waitForProcessDeployment()
        .startProcessInstance(VariablesDTO.of("correlationKey", "key1"))
        .waitForMessageSubscription("ReceiveTaskMessage", Set.of("key1"))
        .andSendMessageWithCorrelationKey(
            "ReceiveTaskMessage", "key1", VariablesDTO.of("var1", "value1"))
        .waitUntilDone();
  }
}
