package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

public class ProcessInstanceAssert {

  private final ProcessInstance processInstance;
  private final BpmnTestEngine bpmnTestEngine;

  public ProcessInstanceAssert(ProcessInstance processInstance, BpmnTestEngine bpmnTestEngine) {
    this.processInstance = processInstance;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public ProcessInstanceAssert isNull() {
    assertThat(processInstance).isNull();
    return this;
  }
  public ProcessInstanceAssert isCompleted() {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(
        ProcessInstanceState.COMPLETED);
    return this;
  }
  public ProcessInstanceAssert hasPassedElement(String elementId, int count) {
    Optional<FlowNodeState> bpmnElementState = processInstance.getFlowNodeStates().get(elementId);
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isPresent();
    assertThat(bpmnElementState.get().getPassedCnt()).as("element " + elementId + " has not passed").isEqualTo(count);
    return this;
  }

  public ProcessInstanceAssert hasPassedElement(String elementId) {
    Optional<FlowNodeState> bpmnElementState = processInstance.getFlowNodeStates().get(elementId);
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isPresent();
    assertThat(bpmnElementState.get().getPassedCnt()).as("element " + elementId + " has not passed").isPositive();
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
    Optional<FlowNodeState> bpmnElementState = processInstance.getFlowNodeStates().get(endEvent1);
    bpmnElementState.ifPresent(flowNodeState -> assertThat(flowNodeState.getPassedCnt()).as(
        "element " + endEvent1 + " has passed").isZero());
    return this;
  }

  public ProcessInstanceAssert hasVariableMatching(String var1, Consumer<Object> consumer)
      throws JsonProcessingException {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    consumer.accept(new ObjectMapper().treeToValue(jsonNode, Object.class));
    return this;
  }

  public void hasState(ProcessInstanceState processInstanceState) {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(processInstanceState);
  }

  public ProcessInstanceAssert hasFailed() {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(ProcessInstanceState.FAILED);
    return this;
  }

  public ProcessInstanceAssert isTerminated() {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(ProcessInstanceState.TERMINATED);
    return this;
  }

  public ProcessInstanceAssert isStillActive() {
    assertThat(processInstance.getProcessInstanceState()).isEqualTo(ProcessInstanceState.ACTIVE);
    return this;
  }
}
