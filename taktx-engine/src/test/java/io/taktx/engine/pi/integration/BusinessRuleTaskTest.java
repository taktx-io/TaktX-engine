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
class BusinessRuleTaskTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void businessRuleTask_matchesPremiumCategory_storesDiscountVariable() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Premium"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasVariableWithValue("discount", 0.2);
  }

  @Test
  void businessRuleTask_matchesStandardCategory_storesDiscountVariable() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Standard"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.1);
  }

  @Test
  void businessRuleTask_wildcardRule_matchesUnknownCategory() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Unknown"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.05);
  }
}
