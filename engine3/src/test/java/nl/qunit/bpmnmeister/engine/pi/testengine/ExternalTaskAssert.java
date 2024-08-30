package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.qunit.bpmnmeister.pi.ExternalTaskTrigger;

public class ExternalTaskAssert {

  private final ExternalTaskTrigger activeExternalTaskTrigger;
  private final BpmnTestEngine bpmnTestEngine;

  public ExternalTaskAssert(ExternalTaskTrigger activeExternalTaskTrigger,
      BpmnTestEngine bpmnTestEngine) {

    this.activeExternalTaskTrigger = activeExternalTaskTrigger;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ExternalTaskAssert hasVariableWithValue(String inputVariable, String expected) {
    assertThat(activeExternalTaskTrigger.getVariables().get(inputVariable).asText()).isEqualTo(expected);
    return this;
  }

  public ExternalTaskAssert doesNotHaveVariable(String variable) {
    assertThat(activeExternalTaskTrigger.getVariables().get(variable)).isNull();
    return this;
  }
}
