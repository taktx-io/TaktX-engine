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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class PrivatePackagesSwitchTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testProcessServiceTaskSingle() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds(
            "sales-configuration.payment-account.retrieve-arrangements",
            "sales-configuration.payment-account.retrieve-type",
            "sales-configuration.payment-account.determine-contractants",
            "customer-interaction.actions.end-process-with-activity",
            "sales-configuration.order.cancel")
        .deployProcessDefinitionAndWait("/bpmn/private-packages-switch.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilUserTaskIsWaitingForResponse("aanvraag-stappen")
        .andCompleteUserTaskWithSuccess(VariablesDTO.of("var2", "value2"))
        .waitUntilExternalTaskIsWaitingForResponse("Activity_0uehql5")
        .andRespondToExternalTaskWithSuccess(
            VariablesDTO.of(
                "paymentAccountArrangementList", List.of("arrangement1", "arrangement2")))
        .waitUntilUserTaskIsWaitingForResponse("selecteer-rekening")
        .andCompleteUserTaskWithSuccess(
            VariablesDTO.of("selectedPaymentAccountArrangement", "arrangement1"))
        .waitUntilExternalTaskIsWaitingForResponse("Activity_0a3nzhe")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("accountType", "student2"))
        .waitUntilExternalTaskIsWaitingForResponse("Activity_1lmrbre/Activity_0k9vm1m")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilCompleted()
        .assertThatProcess()
        .hasPassedElementWithId("StartEvent_switch_retail_packages", 1)
        .hasPassedElementWithId("Gateway_0lkrm2u", 1)
        .hasPassedElementWithId("Activity_0uehql5", 1)
        .hasPassedElementWithId("Gateway_1yg9gvo", 1)
        .hasPassedElementWithId("Activity_0a3nzhe", 1)
        .hasPassedElementWithId("Activity_0goz9aj", 1)
        .hasPassedElementWithId("Event_1wffwmu", 1)
        .hasPassedElementWithId("Activity_1lmrbre/Event_0ho34h3", 1)
        .hasPassedElementWithId("Activity_1lmrbre/Gateway_1f067s6", 1)
        .hasPassedElementWithId("Activity_1lmrbre/Gateway_0u566ex", 1)
        .hasPassedElementWithId("Activity_1lmrbre/Activity_0k9vm1m", 1)
        .hasPassedElementWithId("Activity_1lmrbre/Event_0ltlnng", 1)
        .hasPassedElementWithId("Activity_1lmrbre", 1);
  }
}
