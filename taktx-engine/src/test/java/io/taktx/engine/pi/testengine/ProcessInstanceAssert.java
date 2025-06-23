/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.ProcessInstanceState;
import io.taktx.dto.VariablesDTO;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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

  public ProcessInstanceAssert hasPassedElementWithId(String elementIdPath, int count) {
    List<FlowNodeInstanceDTO> flowNodeInstancesWithElementId =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementIdPath);
    assertThat(flowNodeInstancesWithElementId).isNotEmpty();
    assertThat(flowNodeInstancesWithElementId.getFirst().getPassedCnt()).isEqualTo(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(
      String elementId, Class<?> clazz, int numberOfInstances) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId);

    List<FlowNodeInstanceDTO> instancesOfClass =
        instances.stream().filter(i -> i.getClass().equals(clazz)).toList();
    assertThat(instancesOfClass)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .hasSize(numberOfInstances);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId, int count) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId);
    assertThat(instances)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .hasSize(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId) {
    return hasInstantiatedElementWithId(elementId, 1);
  }

  public ProcessInstanceAssert hasTerminatedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .isNotEmpty();
    assertThat(bpmnElementState.getFirst().isTerminated())
        .as("element " + elementId + " was not terminated")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasFailedElement(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instanceToContinue")
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
        bpmnTestEngine.getFlowNodeInstancesWithElementId(processInstanceKey, elementId);

    assertThat(
            bpmnElementState.isEmpty()
                || bpmnElementState.stream().allMatch(state -> state.getPassedCnt() == 0))
        .as("element with " + elementId + " not found in process instanceToContinue")
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

  public ProcessInstanceAssert hasCollectioneMatching(String var1, Consumer<List> consumer)
      throws JsonProcessingException {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceKey);
    JsonNode jsonNode = variables.get(var1);
    assertThat(jsonNode.isArray()).isTrue();
    List t = new ObjectMapper(new CBORFactory()).treeToValue(jsonNode, List.class);
    consumer.accept(t);
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
