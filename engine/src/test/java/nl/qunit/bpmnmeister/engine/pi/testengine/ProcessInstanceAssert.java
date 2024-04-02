package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public class ProcessInstanceAssert {

  private final ProcessInstance processInstance;
  private final BpmnTestEngine bpmnTestEngine;

  public ProcessInstanceAssert(ProcessInstance processInstance, BpmnTestEngine bpmnTestEngine) {
    this.processInstance = processInstance;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public ProcessInstanceAssert isCompleted() {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(
        ProcessInstanceState.COMPLETED);
    return this;
  }

  public ProcessInstanceAssert hasPassedElement(String elementId) {
    BpmnElementState bpmnElementState = processInstance.getElementStates()
        .get(new BaseElementId(elementId));
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotNull();
    assertThat(bpmnElementState.getPassedCnt()).as("element " + elementId + " has not passed").isPositive();
    return this;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, String value1) {
    return this;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ProcessInstanceAssert hasNotPassedElement(String endEvent1) {
    BpmnElementState bpmnElementState = processInstance.getElementStates()
        .get(new BaseElementId(endEvent1));
    if (bpmnElementState != null) {
      assertThat(bpmnElementState.getPassedCnt()).as("element " + endEvent1 + " has passed").isZero();
    }
    return this;
  }
}
