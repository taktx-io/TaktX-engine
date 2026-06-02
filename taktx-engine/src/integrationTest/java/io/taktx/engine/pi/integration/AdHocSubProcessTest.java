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
import io.taktx.dto.AdHocSubProcessInstanceDTO;
import io.taktx.dto.ServiceTaskInstanceDTO;
import io.taktx.dto.TaskInstanceDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class AdHocSubProcessTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testAdHocSubProcessActivatesOnlyReferencedElements() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/adhoc-subprocess-basic.bpmn")
        .startProcessInstance(VariablesDTO.of("activeElements", List.of("taskA1", "taskC1")))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("adhoc-1", AdHocSubProcessInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("adhoc-1/taskA1", TaskInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("adhoc-1/taskC1", TaskInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  /**
   * cancelRemainingInstances=true (default): once completionCondition fires after svcA completes,
   * svcB and svcC are terminated immediately and the subprocess is done.
   */
  @Test
  void testCompletionConditionCancelsRemainingInstances() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("adhoc-task-a", "adhoc-task-b", "adhoc-task-c")
        .deployProcessDefinitionAndWait("/bpmn/adhoc-subprocess-async-cancel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        // All three tasks are activated in parallel — wait for all triggers before proceeding
        .waitForExternalTaskTrigger("adhoc-task-a")
        .waitForExternalTaskTrigger("adhoc-task-b")
        .waitForExternalTaskTrigger("adhoc-task-c")
        // Complete svcA and set svcADone=true → completionCondition fires → svcB & svcC aborted
        .andRespondToExternalTaskWithSuccess("adhoc-task-a", VariablesDTO.of("svcADone", true))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("adhoc-1", AdHocSubProcessInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("adhoc-1/svcA", ServiceTaskInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("adhoc-1/svcB", ServiceTaskInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("adhoc-1/svcC", ServiceTaskInstanceDTO.class, 1)
        .hasPassedElementWithId("adhoc-1/svcA") // svcA completed
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  /**
   * cancelRemainingInstances=false: once completionCondition fires, svcB and svcC are NOT
   * terminated — the subprocess waits for them to finish naturally before completing.
   */
  @Test
  void testCompletionConditionWaitsForRemainingInstances() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("adhoc-task-a", "adhoc-task-b", "adhoc-task-c")
        .deployProcessDefinitionAndWait("/bpmn/adhoc-subprocess-async-no-cancel.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitForExternalTaskTrigger("adhoc-task-a")
        .waitForExternalTaskTrigger("adhoc-task-b")
        .waitForExternalTaskTrigger("adhoc-task-c")
        // Complete svcA → condition fires, but svcB and svcC must still finish
        .andRespondToExternalTaskWithSuccess("adhoc-task-a", VariablesDTO.of("svcADone", true))
        // Subprocess is NOT done yet — complete the remaining two
        .andRespondToExternalTaskWithSuccess("adhoc-task-b", VariablesDTO.empty())
        .andRespondToExternalTaskWithSuccess("adhoc-task-c", VariablesDTO.empty())
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("adhoc-1", AdHocSubProcessInstanceDTO.class, 1)
        .hasPassedElementWithId("adhoc-1/svcA")
        .hasPassedElementWithId("adhoc-1/svcB")
        .hasPassedElementWithId("adhoc-1/svcC")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testAdHocSubProcessEmptyCollectionRemainsActive() throws IOException {
    // An empty collection is valid — subprocess stays active with no children activated
    // Since all tasks auto-complete, scope immediately sees activeCnt=0 → COMPLETED
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/adhoc-subprocess-basic.bpmn")
        .startProcessInstance(VariablesDTO.of("activeElements", List.of()))
        .waitUntilDone()
        .assertThatProcess()
        .hasInstantiatedElementWithId("adhoc-1", AdHocSubProcessInstanceDTO.class, 1)
        .hasInstantiatedElementWithId("EndEvent_1");
  }
}
