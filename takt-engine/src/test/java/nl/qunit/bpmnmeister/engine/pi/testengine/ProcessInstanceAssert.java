package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.ProcessInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessInstanceState;

public class ProcessInstanceAssert {

  private final UUID processInstanceKey;
  private final BpmnTestEngine bpmnTestEngine;

  public ProcessInstanceAssert(UUID processInstanceId, BpmnTestEngine bpmnTestEngine) {
    this.processInstanceKey = processInstanceId;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public ProcessInstanceAssert isCompleted() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceKey);
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getFlowNodeInstances().getState())
        .isEqualTo(ProcessInstanceState.COMPLETED);
    return this;
  }

  public ProcessInstanceAssert hasPassedElementWithId(String elementId, int... count) {
    assertThat(
            bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, count))
        .isNotEmpty();
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(
      String elementId, Class<?> clazz, int count) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 1);
    assertThat(instances)
        .as("element with " + elementId + " not found in process instance")
        .isNotEmpty();
    assertThat(instances).as("element " + elementId + " has not passed").hasSize(count);
    assertThat(instances.stream())
        .as("element " + elementId + " not of class " + clazz)
        .allMatch(i -> i.getClass().equals(clazz));
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId, int count) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 1);
    assertThat(instances)
        .as("element with " + elementId + " not found in process instance")
        .isNotEmpty();
    assertThat(instances).as("element " + elementId + " has not passed").hasSize(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId) {
    return hasInstantiatedElementWithId(elementId, 1);
  }

  public ProcessInstanceAssert hasTerminatedElements(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 0);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instance")
        .isNotEmpty();
    bpmnElementState.forEach(
        state ->
            assertThat(state.isTerminated())
                .as("element " + elementId + " was not terminated")
                .isTrue());
    return this;
  }

  public ProcessInstanceAssert hasTerminatedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 0);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instance")
        .isNotEmpty();
    assertThat(bpmnElementState.getFirst().isTerminated())
        .as("element " + elementId + " was not terminated")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasFailedElement(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 1);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instance")
        .isNotEmpty();
    assertThat(bpmnElementState.getFirst().isFailed())
        .as("element " + elementId + " was not terminated")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert doesNotHaveVariable(String var1) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceKey);
    JsonNode jsonNode = variables.get(var1);
    assertThat(jsonNode).isNull();
    return this;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, Object value1) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceKey);
    JsonNode jsonNode = variables.get(var1);
    assertThat(jsonNode).isNotNull();
    JsonNode expectedNode = new ObjectMapper(new CBORFactory()).valueToTree(value1);
    assertThat(jsonNode).isEqualTo(expectedNode);
    return this;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ProcessInstanceAssert hasNotPassedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId, 0);

    assertThat(
            bpmnElementState.isEmpty()
                || bpmnElementState.stream().allMatch(state -> state.getPassedCnt() == 0))
        .as("element with " + elementId + " not found in process instance")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasVariableMatching(String var1, Consumer<Object> consumer)
      throws JsonProcessingException {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceKey);
    JsonNode jsonNode = variables.get(var1);
    consumer.accept(new ObjectMapper(new CBORFactory()).treeToValue(jsonNode, Object.class));
    return this;
  }

  public void hasState(ProcessInstanceState processInstanceState) {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceKey);

    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(processInstanceState);
  }

  public ProcessInstanceAssert hasFailed() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceKey);
    assertThat(processInstance.getFlowNodeInstances().getState())
        .isEqualTo(ProcessInstanceState.FAILED);
    return this;
  }

  public ProcessInstanceAssert isTerminated() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceKey);
    assertThat(processInstance.getFlowNodeInstances().getState())
        .isEqualTo(ProcessInstanceState.TERMINATED);
    return this;
  }

  public ProcessInstanceAssert isStillActive() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceKey);
    assertThat(processInstance.getFlowNodeInstances().getState())
        .isEqualTo(ProcessInstanceState.ACTIVE);
    return this;
  }
}
