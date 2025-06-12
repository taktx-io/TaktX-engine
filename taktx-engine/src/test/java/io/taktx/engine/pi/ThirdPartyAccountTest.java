package io.taktx.engine.pi;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pi.testengine.SingletonBpmnTestEngine;
import io.taktx.engine.pi.testengine.TestConfigResource;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(TestConfigResource.class)
class ThirdPartyAccountTest {

  @BeforeEach
  void reset() {
    SingletonBpmnTestEngine.getInstance().reset();
  }

  @Test
  void testFlow() throws IOException {

    SingletonBpmnTestEngine.getInstance()
        .registerAndSubscribeToExternalTaskIds(
            "sales-configuration.order.get-applicant",
            "sales-configuration.customer.get-ascriptions",
            "sales-configuration.related-product.retrieve-edo-arrangements",
            "sales-configuration.payment-account.save-configuration",
            "sales-configuration.payment-account.save-additional-configuration",
            "sales-configuration.related-product.save-edo-configuration",
            "sales-configuration.proposition.get-proposition-configuration",
            "sales-configuration.order.submit",
            "customer-interaction.actions.end-process-with-activity",
            "sales-configuration.order.create",
            "sales-configuration.proposition.get-proposition-configuratio",
            "sales-configuration.proposition.eligibility-check",
            "sales-configuration.order.cancel")
        .deployProcessDefinitionAndWait("/bpmn/select-contractant-call-activity.bpmn")
        .deployProcessDefinitionAndWait(
            "/bpmn/special-business-accounts-configuration-call-activity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/summary-call-activity.bpmn")
        .deployProcessDefinitionAndWait("/bpmn/third-party-current-account-new-modify.bpmn")
        .startProcessInstance(VariablesDTO.of("var1", "value1"))
        .waitUntilChildProcessIsStarted("select-contractant-lb-call-activity")
        .waitUntilExternalTaskIsWaitingForResponse(
            "select-contractant-lb-call-activity:Activity_160mnov")
        .andRespondToExternalTaskWithSuccess(
            VariablesDTO.of("applicantList", List.of("relation1", "relation2")))
        .waitUntilUserTaskIsWaitingForResponse(
            "select-contractant-lb-call-activity:select-contractant")
        .andCompleteUserTaskWithSuccess(VariablesDTO.of("contractant", "relation1"))
        .parentProcess()
        .waitUntilExternalTaskIsWaitingForResponse("THIRD_PARTY_CURRENT_ACCOUNT:Activity_0j81i9n")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("THIRD_PARTY_CURRENT_ACCOUNT:skhfdljshl")
        .andRespondToExternalTaskWithSuccess(
            VariablesDTO.of("eligibilityCheckResult", new ElegibilityCheckResult(true, List.of())))
        .waitUntilExternalTaskIsWaitingForResponse("THIRD_PARTY_CURRENT_ACCOUNT:Activity_0sgumuw")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.empty())
        .waitUntilExternalTaskIsWaitingForResponse("THIRD_PARTY_CURRENT_ACCOUNT:Activity_09wl26r")
        .andRespondToExternalTaskWithSuccess(VariablesDTO.of("flow", "MODIFY"))
        .waitUntilChildProcessIsStarted("special-business-accounts-configuration-call-activity")
        .waitUntilExternalTaskIsWaitingForResponse(
            Map.of(
                "Activity_15ghkvy",
                (bpmnTestEngine, externalTaskTrigger) ->
                    bpmnTestEngine.andRespondToExternalTaskWithSuccess(
                        externalTaskTrigger, VariablesDTO.empty())))
        .waitUntilUserTaskIsWaitingForResponse(
            "special-business-accounts-configuration-call-activity:configure-account");
  }

  private static class ElegibilityCheckResult {
    private final boolean result;
    private final List<Object> errors;

    public ElegibilityCheckResult(boolean result, List<Object> errors) {
      this.result = result;
      this.errors = errors;
    }

    public boolean getResult() {
      return result;
    }

    public List<Object> getErrors() {
      return errors;
    }
  }
}
