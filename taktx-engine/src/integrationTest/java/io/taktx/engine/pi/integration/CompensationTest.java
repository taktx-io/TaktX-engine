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

  /** Req §7 — no activityRef → all completed handlers in scope are invoked concurrently. */
  @Test
  void allHandlersInvokedWhenNoActivityRef() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("task-a", "task-b", "undo-task-a", "undo-task-b")
        .deployProcessDefinitionAndWait("/bpmn/compensation-all-handlers.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-a")
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-b")
        .andRespondToExternalTaskWithSuccess("task-b", VariablesDTO.empty())
        // Both handlers are dispatched concurrently — order is not guaranteed
        .waitForExternalTaskTrigger("undo-task-a")
        .waitForExternalTaskTrigger("undo-task-b")
        .andRespondToExternalTaskWithSuccess("undo-task-a", VariablesDTO.empty())
        .andRespondToExternalTaskWithSuccess("undo-task-b", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("undo-task-a")
        .hasInstantiatedElementWithId("undo-task-b");
  }

  /** Req §4 — compensation can also be triggered from an end event. */
  @Test
  void compensationEndEventTriggersHandler() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("task-a", "undo-task-a")
        .deployProcessDefinitionAndWait("/bpmn/compensation-end-event.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-a")
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.empty())
        .waitForExternalTaskTrigger("undo-task-a")
        .andRespondToExternalTaskWithSuccess("undo-task-a", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("undo-task-a");
  }

  /**
   * Req §12 — the handler receives the variable snapshot captured when the compensatable activity
   * completed, not the current (possibly mutated) process state.
   */
  @Test
  void handlerReceivesVariableSnapshotFromActivityCompletion() throws IOException {
    BpmnTestEngine engine = SingletonBpmnTestEngine.getInstance();
    engine
        .registerAndSubscribeToExternalTaskIds("task-a", "undo-task-a")
        .deployProcessDefinitionAndWait("/bpmn/compensation-simple.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("task-a")
        // task-a outputs a variable; this should be snapshotted for the handler
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.of("output", "captured-value"))
        .waitForExternalTaskTrigger("undo-task-a");

    // The snapshot is available as input variables on the handler trigger
    ExternalTaskTriggerDTO handlerTrigger = engine.getActiveExternalTaskTrigger("undo-task-a");
    assertThat(handlerTrigger).isNotNull();
    assertThat(handlerTrigger.getVariables().getVariables()).containsKey("output");

    engine
        .andRespondToExternalTaskWithSuccess("undo-task-a", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted();
  }

  /**
   * Req §5 — a compensation throw event inside a subprocess only compensates activities in that
   * subprocess scope. Process-level compensation registrations are not touched.
   */
  @Test
  void throwInsideSubprocessOnlyCompensatesWithinSubprocessScope() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("task-a", "task-b", "undo-task-b")
        .deployProcessDefinitionAndWait("/bpmn/compensation-subprocess-scope.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        // task-a completes at process level (creates a process-level compensation registration)
        .waitForExternalTaskTrigger("task-a")
        .andRespondToExternalTaskWithSuccess("task-a", VariablesDTO.empty())
        // task-b completes inside the subprocess
        .waitForExternalTaskTrigger("task-b")
        .andRespondToExternalTaskWithSuccess("task-b", VariablesDTO.empty())
        // compensation throw is INSIDE the subprocess — only undo-task-b should fire
        .waitForExternalTaskTrigger("undo-task-b")
        .andRespondToExternalTaskWithSuccess("undo-task-b", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .isCompleted()
        .hasInstantiatedElementWithId("undo-task-b")
        // undo-task-a must NOT have been invoked — process-level scope was not touched
        .hasNotPassedElementWithId("undo-task-a");
  }
}
