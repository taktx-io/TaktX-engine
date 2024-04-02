package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;
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
  public ProcessInstanceAssert hasPassedElement(String elementId, int count) {
    BpmnElementState bpmnElementState = processInstance.getElementStates()
        .get(new BaseElementId(elementId));
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotNull();
    assertThat(bpmnElementState.getPassedCnt()).as("element " + elementId + " has not passed").isEqualTo(count);
    return this;
  }

  public ProcessInstanceAssert hasPassedElement(String elementId) {
    BpmnElementState bpmnElementState = processInstance.getElementStates()
        .get(new BaseElementId(elementId));
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotNull();
    assertThat(bpmnElementState.getPassedCnt()).as("element " + elementId + " has not passed").isPositive();
    return this;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, Object value1) {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    JsonNode expectedNode = new ObjectMapper().valueToTree(value1);
    assertThat(jsonNode).isEqualTo(expectedNode);
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

  public ProcessInstanceAssert hasVariableMatching(String var1, Consumer<Object> consumer)
      throws JsonProcessingException {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    consumer.accept(new ObjectMapper().treeToValue(jsonNode, Object.class));
    return this;
  }
}
