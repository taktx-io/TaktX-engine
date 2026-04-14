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

/**
 * Integration tests for DRG (Decision Requirements Graph) support: a BPMN business-rule task calls
 * {@code discountDecision}, which itself requires {@code categoryDecision}. The engine must
 * evaluate the upstream decision first and make its result available to the downstream one.
 *
 * <p>DMN file: {@code /dmn/discount-drg.dmn}<br>
 * Chain: {@code loyaltyPoints} → categoryDecision → discountDecision → {@code discount}
 */
@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class BusinessRuleTaskDrgTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void drg_premiumLoyaltyPoints_storesMaxDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 1500))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_1", 1)
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasPassedElementWithId("EndEvent_1", 1)
        .hasVariableWithValue("discount", 0.2);
  }

  @Test
  void drg_standardLoyaltyPoints_storesStandardDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 750))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.1);
  }

  @Test
  void drg_basicLoyaltyPoints_storesMinimumDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 100))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.05);
  }

  @Test
  void drg_multiOutputPremiumLoyaltyPoints_storesMaxDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg-multi-output.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 1500))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.2);
  }

  @Test
  void drg_multiOutputStandardLoyaltyPoints_storesStandardDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg-multi-output.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 750))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.1);
  }

  @Test
  void drg_multiOutputBasicLoyaltyPoints_storesMinimumDiscount() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg-multi-output.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 100))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.05);
  }

  @Test
  void drg_collectRequiredDecisionResultCanBeIndexedExplicitly() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg-collect.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 1500))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.2);
  }

  @Test
  void drg_collectRequiredDecisionSingleMatchCanStillBeIndexedExplicitly() throws IOException {
    SingletonBpmnTestEngine.getInstance()
        .deployDmnDefinitionAndWait("/dmn/discount-drg-collect.dmn")
        .deployProcessDefinitionAndWait("/bpmn/business-rule-task.bpmn")
        .startProcessInstance(VariablesDTO.of("loyaltyPoints", 750))
        .waitUntilDone()
        .assertThatProcess()
        .hasPassedElementWithId("BusinessRuleTask_1", 1)
        .hasVariableWithValue("discount", 0.1);
  }
}
