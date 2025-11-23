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
        .waitForExternalTaskTrigger("external-task")
        .andRespondToExternalTaskWithSuccess("external-task", VariablesDTO.empty())
        .waitUntilIncident()
        .assertThatProcess()
        .isIncident()
        .hasPassedElementWithId("Activity_0cxnpbx")
        .hasPassedElementWithId("Activity_1ohwsp7")
        .hasPassedElementWithId("Activity_09g9dzh")
        .hasPassedElementWithId("Event_1h5ln3k");
  }
}
