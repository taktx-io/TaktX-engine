package io.taktx.engine.pi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.time.Duration;
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
        .deployProcessDefinitionAndWait(
            "/bpmn/private-packages-switch.bpmn",
            "sales-configuration.payment-account.retrieve-arrangements",
            "sales-configuration.payment-account.retrieve-type",
            "sales-configuration.payment-account.determine-contractants")
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
        .waitFor(Duration.ofSeconds(10));
  }
}
