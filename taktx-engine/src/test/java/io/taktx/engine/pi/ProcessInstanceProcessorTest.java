/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

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
import org.junit.jupiter.api.Disabled;
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
  void testProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-single.bpmn")
        .startProcessInstance(VariablesDTO.of("key1", "value1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("key1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/subprocess-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessServiceTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("servicetask")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("SubTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("SubProcess_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testSubProcessTaskNested() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/subprocess-servicetask-nested.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse(
            "SubProcess_1/SubSubProcess_1.aaa/SubSubSubProcess_1/SubTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
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
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("task-callactivity:callactivity-id")
        .hasInstantiatedElementWithId("task-callactivity:EndEvent_1");
  }

  @Test
  void testScheduledStart_R5() throws IOException {
    // All executed timers fall within the same time bucket
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r5.bpmn")
        .moveTimeForward(Duration.ofMillis(2001))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(2000))
        .waitForNewProcessInstance()
        .waitUntilCompleted();
  }

  @Test
  void testScheduledStart_R60() throws IOException {
    // The last timer falls into the next time bucket
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/schedule_start_r60.bpmn")
        .moveTimeForward(Duration.ofMillis(20001))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance(Duration.ofMillis(60000))
        .waitUntilCompleted()
        .moveTimeForward(Duration.ofMillis(20000))
        .waitForNewProcessInstance()
        .waitUntilCompleted();
  }

  @Test
  @Disabled
  void testTerminateSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsActive("SubTask_1")
        .terminateProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  void testTerminateChildSingleCallActivityInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity_servicetask.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-callactivity.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilChildProcessIsStarted("calledActivityServiceTask")
        .parentProcess()
        .terminateProcessInstance()
        .waitUntilChildProcessIsTerminated("calledActivityServiceTask")
        .assertThatProcess()
        .isTerminated()
        .toProcessLevel()
        .parentProcess()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  @Disabled
  void testTerminateSpecificSingleElementInstances() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/terminate-single-child-elements.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilElementIsWaiting("SubTask_1")
        .terminateElementInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasTerminatedElementWithId("SubProcess_1")
        .isTerminated();
  }

  @Test
  void testMessageStartEvent() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/message_start.bpmn")
        .waitForMessageSubscription("StartMessage2")
        .sendMessage("StartMessage2", VariablesDTO.of("var1", "value1"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var1", "value1")
        .toProcessLevel()
        .sendMessage("StartMessage2", VariablesDTO.of("var2", "value2"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("EndEvent_1")
        .hasVariableWithValue("var2", "value2")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/message_start_2.bpmn")
        .waitForMessageSubscription("StartMessage3")
        .sendMessage("StartMessage3", VariablesDTO.of("var3", "value3"))
        .waitForNewProcessInstance()
        .waitUntilCompleted()
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
        .waitUntilCompleted();
  }
}
