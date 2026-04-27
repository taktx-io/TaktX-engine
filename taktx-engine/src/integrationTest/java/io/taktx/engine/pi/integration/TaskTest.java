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
class TaskTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/task-single.bpmn")
        .startProcessInstance(VariablesDTO.of("key1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("key1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("Task_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }

  @Test
  void testProcessManualTaskSingle() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployProcessDefinitionAndWait("/bpmn/manual-task.bpmn")
        .startProcessInstance(VariablesDTO.of("key1", "value1"))
        .waitUntilDone()
        .assertThatProcess()
        .hasVariableWithValue("key1", "value1")
        .hasInstantiatedElementWithId("StartEvent_1")
        .hasInstantiatedElementWithId("ManualTask_1")
        .hasInstantiatedElementWithId("EndEvent_1");
  }
}
