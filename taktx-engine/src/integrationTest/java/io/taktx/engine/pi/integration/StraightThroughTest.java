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
