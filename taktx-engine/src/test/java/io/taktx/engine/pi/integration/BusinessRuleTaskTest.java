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

  /**
   * Regression test for the "no output mappings → propagate all" bug.
   *
   * <p>When a BusinessRuleTask has no explicit {@code zeebe:ioMapping} output mappings the result
   * variable must still be visible to downstream nodes (Zeebe-like behaviour). Without the fix the
   * gateway cannot evaluate {@code =discount >= 0.2} because {@code discount} is trapped in the
   * task's local scope and never copied to the process scope, so the gateway falls through to the
   * default path instead of the premium path.
   */
  @Test
  void businessRuleTask_withGateway_resultVariablePropagatedToProcessScopeWhenNoOutputMappings()
      throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Premium"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasPassedElementWithId("Gateway_1", 1)
        // discount = 0.2, condition "=discount >= 0.2" must be TRUE → premium path taken
        .hasPassedElementWithId("EndEvent_Premium", 1)
        .hasNotPassedElementWithId("EndEvent_Default");
  }

  @Test
  void businessRuleTask_withGateway_standardCategoryTakesDefaultPath() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task-gateway.bpmn")
        .startProcessInstance(VariablesDTO.of("category", "Standard"))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasPassedElementWithId("Gateway_1", 1)
        // discount = 0.1, condition "=discount >= 0.2" must be FALSE → default path taken
        .hasPassedElementWithId("EndEvent_Default", 1)
        .hasNotPassedElementWithId("EndEvent_Premium");
  }
}
