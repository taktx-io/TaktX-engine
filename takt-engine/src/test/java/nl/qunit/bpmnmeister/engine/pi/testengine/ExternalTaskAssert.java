package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ExternalTaskTriggerDTO;

public class ExternalTaskAssert {

  private final ExternalTaskTriggerDTO activeExternalTaskTrigger;
  private final BpmnTestEngine bpmnTestEngine;

  public ExternalTaskAssert(
      ExternalTaskTriggerDTO activeExternalTaskTrigger, BpmnTestEngine bpmnTestEngine) {

    this.activeExternalTaskTrigger = activeExternalTaskTrigger;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ExternalTaskAssert hasVariableWithValue(String inputVariable, String expected) {
    assertThat(activeExternalTaskTrigger.getVariables().get(inputVariable).asText())
        .isEqualTo(expected);
    return this;
  }

  public ExternalTaskAssert doesNotHaveVariable(String variable) {
    assertThat(activeExternalTaskTrigger.getVariables().get(variable)).isNull();
    return this;
  }
}
