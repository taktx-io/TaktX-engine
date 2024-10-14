package nl.qunit.bpmnmeister.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import nl.qunit.bpmnmeister.pi.FlowNodeInstancesDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;
import nl.qunit.bpmnmeister.pi.ProcessInstanceUpdate;
import nl.qunit.bpmnmeister.pi.state.FlowNodeInstanceDTO;
import nl.qunit.bpmnmeister.pi.state.WithFlowNodeInstancesDTO;

public class ProcessInstanceAssert {

  private final ProcessInstanceUpdate processInstance;
  private final BpmnTestEngine bpmnTestEngine;

  public ProcessInstanceAssert(ProcessInstanceUpdate processInstance, BpmnTestEngine bpmnTestEngine) {
    this.processInstance = processInstance;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public ProcessInstanceAssert isNull() {
    assertThat(processInstance).isNull();
    return this;
  }
  public ProcessInstanceAssert isCompleted() {
    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(
        ProcessInstanceState.COMPLETED);
    return this;
  }

  public ProcessInstanceAssert hasPassedElementWithId(String elementId, int count) {
    Optional<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0).stream()
        .findFirst();

    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isPresent();
    assertThat(bpmnElementState.get().getPassedCnt()).as("element " + elementId + " has not passed " + count + " times ").isEqualTo(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId, Class<?> clazz, int count) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0).stream()
        .filter(s -> s.getPassedCnt() > 0)
        .filter(s -> s.getClass().equals(clazz))
        .toList();

    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    assertThat(bpmnElementState).as("element " + elementId + " has not passed").hasSize(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId, int count) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0).stream()
        .filter(s -> s.getPassedCnt() > 0)
        .toList();
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    assertThat(bpmnElementState).as("element " + elementId + " has not passed").hasSize(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = processInstance.getFlowNodeInstances()
        .get(elementId).stream()
        .filter(s -> s.getPassedCnt() > 0)
        .toList();
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    assertThat(bpmnElementState).as("element " + elementId + " has not passed").isNotEmpty();
    return this;
  }

  public ProcessInstanceAssert hasTerminatedElements(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0);
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    bpmnElementState.forEach(state ->
        assertThat(state.isTerminated()).as("element " + elementId + " was not terminated").isTrue());
    return this;
  }

  public ProcessInstanceAssert hasTerminatedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0);
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    assertThat(bpmnElementState.get(0).isTerminated()).as("element " + elementId + " was not terminated").isTrue();
    return this;
  }

  private List<FlowNodeInstanceDTO> getFlowNodeInstancesWithElementId(FlowNodeInstancesDTO flowNodeInstances,
      String elementId, int index) {
    String[] split = elementId.split("/");
    String elementIdSubPath = split[index];
    return flowNodeInstances.get(elementIdSubPath).stream().flatMap(flowNodeInstanceDTO -> {
      if (split.length > index + 1 && flowNodeInstanceDTO instanceof WithFlowNodeInstancesDTO withFlowNodeInstances) {
        return getFlowNodeInstancesWithElementId(withFlowNodeInstances.getFlowNodeInstances(), elementId, index + 1).stream();
      } else {
        return Stream.of(flowNodeInstanceDTO);
      }
    }).toList();
  }

  public ProcessInstanceAssert hasFailedElement(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0);
    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isNotEmpty();
    assertThat(bpmnElementState.get(0).isFailed()).as("element " + elementId + " was not terminated").isTrue();
    return this;
  }

  public ProcessInstanceAssert doesNotHaveVariable(String var1) {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    assertThat(jsonNode).isNull();
    return this;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, Object value1) {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    assertThat(jsonNode).isNotNull();
    JsonNode expectedNode = new ObjectMapper().valueToTree(value1);
    assertThat(jsonNode).isEqualTo(expectedNode);
    return this;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ProcessInstanceAssert hasNotPassedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstancesWithElementId(processInstance.getFlowNodeInstances(), elementId, 0).stream().filter(s -> s.getPassedCnt() > 0).toList();

    assertThat(bpmnElementState).as("element with " + elementId + " not found in process instance").isEmpty();
    return this;
  }

  public ProcessInstanceAssert hasVariableMatching(String var1, Consumer<Object> consumer)
      throws JsonProcessingException {
    JsonNode jsonNode = processInstance.getVariables().get(var1);
    consumer.accept(new ObjectMapper().treeToValue(jsonNode, Object.class));
    return this;
  }

  public void hasState(ProcessInstanceState processInstanceState) {
    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(processInstanceState);
  }

  public ProcessInstanceAssert hasFailed() {
    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(ProcessInstanceState.FAILED);
    return this;
  }

  public ProcessInstanceAssert isTerminated() {
    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(ProcessInstanceState.TERMINATED);
    return this;
  }

  public ProcessInstanceAssert isStillActive() {
    assertThat(processInstance.getFlowNodeInstances().getState()).isEqualTo(ProcessInstanceState.ACTIVE);
    return this;
  }
}
