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
class StraightThroughTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testStraighThrough() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds("external-task")
        .deployProcessDefinitionAndWait("/bpmn/straight-through.bpmn")
        .startProcessInstance(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("ExternalTask_1")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .isTerminated()
        .hasTerminatedElementWithId("ExternalTask_1")
        .hasNotPassedElementWithId("Activity_0cxnpbx")
        .hasNotPassedElementWithId("Activity_1ohwsp7")
        .hasNotPassedElementWithId("Activity_09g9dzh")
        .hasNotPassedElementWithId("Event_1h5ln3k");
  }
}
