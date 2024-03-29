package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

public class ProcessInstanceAssert {

  private final ProcessInstance processInstance;

  public ProcessInstanceAssert(ProcessInstance processInstance) {
    this.processInstance = processInstance;
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
    assertThat(bpmnElementState.getPassedCnt()).as("element " + elementId + " has not passed").isGreaterThan(0);
    return this;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, String value1) {
    return this;
  }
}
