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
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.BpmnTestEngine;
import io.taktx.engine.pi.testengine.ProcessInstanceAssert;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class DefinitionsProtoAcceptanceTest {

  private static final String SCRIPT_JOBWORKER_TASK_ID = "script-jobworker";
  private static final String BOUNDARY_SERVICE_TASK_ID = "service-task-id";
  private static final String USER_TASK_VARIABLE = "usertask";
  private static final String CALLED_ACTIVITY_PROCESS_ID = "calledActivity";
  private static final String VERSION_TASK_V1 = "proto45-version-task-v1";
  private static final String VERSION_TASK_V2 = "proto45-version-task-v2";

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void deployAndExecuteRepresentativeSupportedElementFamiliesCompleteEndToEnd() throws IOException {
    BpmnTestEngine engine = SingletonBpmnTestEngine.getInstance();

    engine.registerAndSubscribeToExternalTaskIds(
        SCRIPT_JOBWORKER_TASK_ID, BOUNDARY_SERVICE_TASK_ID);

    engine
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Premium"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasPassedElementWithId("Gateway_1", 1)
        .hasPassedElementWithId("EndEvent_Premium", 1)
        .hasNotPassedElementWithId("EndEvent_Default")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/usertask.bpmn")
        .startProcessInstance(VariablesDTO.of(USER_TASK_VARIABLE, "acceptance"))
        .waitUntilUserTaskIsWaitingForResponse("UserTask_1")
        .assertThatUserTask()
        .hasAssignee("assignee")
        .hasCandidateGroups("candidategroups")
        .hasCandidateUsers("candidateusers")
        .hasPriority("10")
        .toProcessLevel()
        .andCompleteUserTaskWithSuccess(VariablesDTO.of(USER_TASK_VARIABLE, "completed"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("UserTask_1", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasVariableWithValue(USER_TASK_VARIABLE, "completed")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/script-tasks.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger(SCRIPT_JOBWORKER_TASK_ID)
        .andRespondToExternalTaskWithSuccess(
            SCRIPT_JOBWORKER_TASK_ID, VariablesDTO.of("jobWorkerResult", 456))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("FeelScriptTask_1", 1)
        .hasPassedElementWithId("JobWorkerScriptTask_1", 1)
        .hasVariableWithValue("feelResult", 123)
        .hasVariableWithValue("jobWorkerResult", 456)
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/boundary-timer.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger(BOUNDARY_SERVICE_TASK_ID)
        .waitUntilIdle()
        .moveTimeForward(Duration.ofMinutes(10).plusMillis(1))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("Boundary_Timer_1", 1)
        .hasPassedElementWithId("EndEvent_2", 1)
        .hasNotPassedElementWithId("EndEvent_1")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/link-intermediate-catch-throw.bpmn")
        .startProcessInstance(VariablesDTO.of("input", "value"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("Throw_1", 1)
        .hasPassedElementWithId("Catch_1", 1)
        .hasNotPassedElementWithId("Catch_2")
        .hasVariableWithValue("linkOutput_1", 123)
        .hasVariableWithValue("linkOutput_2", 456)
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/event-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("inputVariable", "proto45-correlation"))
        .waitUntilIdle()
        .andSendMessageWithCorrelationKey("Msg", "proto45-correlation", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("Gateway_0wn8ufc", 1)
        .hasPassedElementWithId("Message_Event", 1)
        .hasPassedElementWithId("Message_End_Event", 1)
        .hasAbortedElementWithId("Timer_Event")
        .hasAbortedElementWithId("Signal_Event")
        .toProcessLevel()
        .deployProcessDefinitionAndWait("/bpmn/calledActivity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/callactivity-single.bpmn")
        .startProcessInstance(
            VariablesDTO.of(
                CALLED_ACTIVITY_PROCESS_ID, CALLED_ACTIVITY_PROCESS_ID, "var1", "acceptance"))
        .waitUntilChildProcessIsCompleted(CALLED_ACTIVITY_PROCESS_ID)
        .assertThatProcess()
        .hasPassedElementWithId(CALLED_ACTIVITY_PROCESS_ID + ":StartEvent_CalledElement", 1)
        .hasPassedElementWithId(CALLED_ACTIVITY_PROCESS_ID + ":task_CalledElement", 1)
        .hasPassedElementWithId(CALLED_ACTIVITY_PROCESS_ID + ":EndEvent_CalledElement", 1)
        .toProcessLevel()
        .assertThatParentProcess()
        .hasPassedElementWithId("task-callactivity:StartEvent_1", 1)
        .toProcessLevel()
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("task-callactivity:callactivity-id", 1)
        .hasPassedElementWithId("task-callactivity:EndEvent_1", 1);
  }

  @Test
  void redeployVersionBumpExistingInstancesContinueAndNewInstancesUseLatestVersion()
      throws IOException {
    BpmnTestEngine engine = SingletonBpmnTestEngine.getInstance();
    engine.registerAndSubscribeToExternalTaskIds(VERSION_TASK_V1, VERSION_TASK_V2);

    engine
        .deployProcessDefinitionAndWait("/bpmn/proto45-versioned-process-v1.bpmn")
        .startProcessInstance(VariablesDTO.of("payload", "legacy"))
        .waitForExternalTaskTrigger(VERSION_TASK_V1);

    UUID legacyInstanceId =
        engine.getProcessInstanceMap().keySet().stream().findFirst().orElseThrow();
    ExternalTaskTriggerDTO legacyTrigger =
        Objects.requireNonNull(engine.getActiveExternalTaskTrigger(VERSION_TASK_V1));

    assertThat(engine.getProcessInstance(legacyInstanceId).getProcessDefinitionKey().getVersion())
        .isEqualTo(1);

    engine
        .deployProcessDefinitionAndWait("/bpmn/proto45-versioned-process-v2.bpmn")
        .startProcessInstance(VariablesDTO.of("payload", "current"))
        .waitForExternalTaskTrigger(VERSION_TASK_V2);

    UUID currentInstanceId =
        engine.getProcessInstanceMap().keySet().stream()
            .filter(processInstanceId -> !processInstanceId.equals(legacyInstanceId))
            .findFirst()
            .orElseThrow();
    ExternalTaskTriggerDTO currentTrigger =
        Objects.requireNonNull(engine.getActiveExternalTaskTrigger(VERSION_TASK_V2));

    assertThat(engine.getProcessInstance(currentInstanceId).getProcessDefinitionKey().getVersion())
        .isEqualTo(2);

    engine.andRespondToExternalTaskWithSuccess(
        legacyTrigger, VariablesDTO.of("legacyVersion", 1, "legacyPayload", "done"));

    Awaitility.await()
        .atMost(BpmnTestEngine.DEFAULT_DURATION)
        .untilAsserted(() -> new ProcessInstanceAssert(legacyInstanceId, engine).isCompleted());

    new ProcessInstanceAssert(legacyInstanceId, engine)
        .hasVariableWithValue("legacyVersion", 1)
        .hasVariableWithValue("legacyPayload", "done");

    engine.andRespondToExternalTaskWithSuccess(
        currentTrigger, VariablesDTO.of("currentVersion", 2));

    Awaitility.await()
        .atMost(BpmnTestEngine.DEFAULT_DURATION)
        .untilAsserted(() -> new ProcessInstanceAssert(currentInstanceId, engine).isCompleted());

    new ProcessInstanceAssert(currentInstanceId, engine)
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasPassedElementWithId("ServiceTask_v2", 1)
        .hasPassedElementWithId("Task_v2", 1)
        .hasPassedElementWithId("EndEvent_v2", 1)
        .hasVariableWithValue("currentVersion", 2);

    assertThat(engine.getProcessInstance(legacyInstanceId).getProcessDefinitionKey().getVersion())
        .isEqualTo(1);
    assertThat(engine.getProcessInstance(currentInstanceId).getProcessDefinitionKey().getVersion())
        .isEqualTo(2);
  }
}
