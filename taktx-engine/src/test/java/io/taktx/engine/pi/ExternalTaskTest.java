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
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class ExternalTaskTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessServiceTaskSingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithSuccess("service-task", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("MappedOutputVariable", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskSingleFx() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-fx.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithSuccess("service-task-id", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ServiceTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessServiceTaskFailed5Retries() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitUntilElementHasAborted("ServiceTask_1")
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasNotPassedElementWithId("ServiceTask_1")
        .isAborted();
  }

  @Test
  void testProcessServiceTaskFailed3RetriesButThenSucceeds() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithError("service-task", true, "fail", "failure")
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithSuccess("service-task", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted();
  }

  @Test
  void testProcessServiceTaskRetryBackoff() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task-id")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single-retry-backoff.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithError("service-task-id", true, "fail", "failure")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithError("service-task-id", true, "fail", "failure")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithError("service-task-id", true, "fail", "failure")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofMillis(3001))
        .waitForExternalTaskTrigger("service-task-id")
        .andRespondToExternalTaskWithSuccess("service-task-id", VariablesDTO.of("success", "true"))
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasVariableMatching("success", val -> assertThat(val).isEqualTo("true"));
  }

  @Test
  void testSendTask_Single() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("send-task-id")
        .deployProcessDefinitionAndWait("/bpmn/sendtask-single.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("send-task-id")
        .andRespondToExternalTaskWithSuccess("send-task-id", VariablesDTO.of("var1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("var1", "value1")
        .hasInstantiatedElementWithId("StartEvent_2")
        .hasInstantiatedElementWithId("send-task-id")
        .hasInstantiatedElementWithId("EndEvent_2");
  }

  @Test
  void testProcessServiceTaskPromise() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("service-task")
        .deployProcessDefinitionAndWait("/bpmn/servicetask-single.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitForExternalTaskTrigger("service-task")
        .andRespondToExternalTaskWithPromise("service-task", "P10D")
        .waitFor(Duration.ofSeconds(1))
        .moveTimeForward(Duration.ofDays(7).plusMillis(1))
        .waitFor(Duration.ofSeconds(1))
        .assertThatProcess()
        .isStillActive()
        .toProcessLevel()
        .moveTimeForward(Duration.ofDays(3))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasAbortedElementWithId("ServiceTask_1")
        .hasNotPassedElementWithId("EndEvent_1");
  }
}
