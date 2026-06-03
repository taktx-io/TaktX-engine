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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class CompensationTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void compensationHandlerIsInvokedAfterActivityCompletes() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("task-a", "undo-task-a")
        .deployProcessDefinitionAndWait("/bpmn/compensation-simple.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-a")
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.empty())
        .waitForExternalTaskTrigger("undo-task-a")
        .andRespondToExternalTaskWithSuccess("undo-task-a", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("start")
        .hasInstantiatedElementWithId("task-a")
        .hasInstantiatedElementWithId("throw-compensate")
        .hasInstantiatedElementWithId("undo-task-a")
        .hasInstantiatedElementWithId("end");
  }

  @Test
  void activityRefTargetsSpecificActivity() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("task-a", "task-b", "undo-task-a")
        .deployProcessDefinitionAndWait("/bpmn/compensation-activity-ref.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-a")
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-b")
        .andRespondToExternalTaskWithSuccess("task-b", VariablesDTO.empty())
        .waitForExternalTaskTrigger("undo-task-a")
        .andRespondToExternalTaskWithSuccess("undo-task-a", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("undo-task-a")
        .hasNotPassedElementWithId("undo-task-b");
  }
}
