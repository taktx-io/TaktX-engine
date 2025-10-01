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
class ScriptTaskTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testZeebeUserTask_CompleteSuccesFully() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("script-jobworker")
        .deployProcessDefinitionAndWait("/bpmn/script-tasks.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("JobWorkerScriptTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("jobWorkerResult", 456))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasPassedElementWithId("FeelScriptTask_1", 1)
        .hasPassedElementWithId("JobWorkerScriptTask_1", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasVariableWithValue("jobWorkerResult", 456)
        .hasVariableWithValue("feelResult", 123);
  }
}
