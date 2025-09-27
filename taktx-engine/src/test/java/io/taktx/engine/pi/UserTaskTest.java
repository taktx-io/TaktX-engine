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
class UserTaskTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testZeebeUserTask_CompleteSuccesFully() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds()
        .deployProcessDefinitionAndWait("/bpmn/usertask.bpmn")
        .startProcessInstance(VariablesDTO.of("usertask", "1"))
        .waitUntilUserTaskIsWaitingForResponse("UserTask_1")
        .assertThatUserTask()
        .hasAssignee("assignee")
        .hasCandidateGroups("candidategroups")
        .hasCandidateUsers("candidateusers")
        .hasPriority("10")
        .hasDueDate("2010-01-01")
        .hasFollowupDate("2020-02-02")
        .toProcessLevel()
        .andCompleteUserTaskWithSuccess(VariablesDTO.of("usertask", "1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("usertask", "1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("UserTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testZeebeUserTask_CompleteWithError() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds()
        .deployProcessDefinitionAndWait("/bpmn/usertask.bpmn")
        .startProcessInstance(VariablesDTO.of("usertask", "1"))
        .waitUntilUserTaskIsWaitingForResponse("UserTask_1")
        .andCompleteUserTaskWithError("code", "message", VariablesDTO.of("usertask", "1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("usertask", "1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("UserTask_1")
        .hasAbortedElementWithId("UserTask_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("EndEvent_Error");
  }

  @Test
  void testZeebeUserTask_CompleteWithEscalation() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds()
        .deployProcessDefinitionAndWait("/bpmn/usertask.bpmn")
        .startProcessInstance(VariablesDTO.of("usertask", "1"))
        .waitUntilUserTaskIsWaitingForResponse("UserTask_1")
        .andCompleteUserTaskWithEscalation("code2", "message", VariablesDTO.of("usertask", "1"))
        .waitUntilCompleted()
        .assertThatProcess()
        .hasVariableWithValue("usertask", "1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("UserTask_1")
        .hasAbortedElementWithId("UserTask_1")
        .hasNotPassedElementWithId("EndEvent_1")
        .hasInstantiatedElementWithId("EndEvent_Escalation");
  }
}
