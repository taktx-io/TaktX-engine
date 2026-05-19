/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExecutionState;
import io.taktx.dto.FlowNodeInstanceDTO;
import io.taktx.dto.ProcessInstanceDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ProcessInstanceAssert {

  private final UUID processInstanceId;
  private final BpmnTestEngine bpmnTestEngine;

  public ProcessInstanceAssert(UUID processInstanceId, BpmnTestEngine bpmnTestEngine) {
    this.processInstanceId = processInstanceId;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public ProcessInstanceAssert isCompleted() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance).isNotNull();
    assertThat(processInstance.getScope().getState()).isEqualTo(ExecutionState.COMPLETED);
    return this;
  }

  public ProcessInstanceAssert hasPassedElementWithId(String elementIdPath) {
    return hasPassedElementWithId(elementIdPath, 1);
  }

  public ProcessInstanceAssert hasPassedElementWithId(String elementIdPath, int count) {
    List<FlowNodeInstanceDTO> scopeWithElementId =
        bpmnTestEngine.getScopeWithElementId(processInstanceId, elementIdPath);
    assertThat(scopeWithElementId).isNotEmpty();
    assertThat(scopeWithElementId.getFirst().getPassedCnt()).isEqualTo(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(
      String elementId, Class<?> clazz, int numberOfInstances) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getScopeWithElementId(processInstanceId, elementId);

    List<FlowNodeInstanceDTO> instancesOfClass =
        instances.stream().filter(i -> i.getClass().equals(clazz)).toList();
    assertThat(instancesOfClass)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .hasSize(numberOfInstances);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId, int count) {
    List<FlowNodeInstanceDTO> instances =
        bpmnTestEngine.getScopeWithElementId(processInstanceId, elementId);
    assertThat(instances)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .hasSize(count);
    return this;
  }

  public ProcessInstanceAssert hasInstantiatedElementWithId(String elementId) {
    return hasInstantiatedElementWithId(elementId, 1);
  }

  public ProcessInstanceAssert hasAbortedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstanceDTOS(elementId);
    assertThat(bpmnElementState.getFirst().isAborted())
        .as("element " + elementId + " was not terminated")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasIncidentElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstanceDTOS(elementId);
    assertThat(bpmnElementState.getFirst().isIncident())
        .as("element " + elementId + " was not in incident")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasCompletedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState = getFlowNodeInstanceDTOS(elementId);
    assertThat(bpmnElementState.getFirst().isCompleted())
        .as("element " + elementId + " was not terminated")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert doesNotHaveVariable(String var1) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceId);
    VariableValue variableValue = variables.get(var1);
    assertThat(variableValue).isNull();
    return this;
  }

  private List<FlowNodeInstanceDTO> getFlowNodeInstanceDTOS(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getScopeWithElementId(processInstanceId, elementId);
    assertThat(bpmnElementState)
        .as("element with " + elementId + " not found in process instanceToContinue")
        .isNotEmpty();
    return bpmnElementState;
  }

  public ProcessInstanceAssert hasVariableWithValue(String var1, Object value1) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceId);
    VariableValue variableValue = variables.get(var1);
    assertThat(variableValue)
        .as(
            "variable "
                + var1
                + " not found in process instanceToContinue, available variables:"
                + variables)
        .isNotNull();
    Object actualValue = Variables.toJavaObject(variableValue);
    if (actualValue instanceof Number actualNumber && value1 instanceof Number expectedNumber) {
      assertThat(new BigDecimal(String.valueOf(actualNumber)))
          .as(
              "variable "
                  + var1
                  + " not found in process instanceToContinue, available variables:"
                  + variables)
          .isEqualTo(new BigDecimal(String.valueOf(expectedNumber)));
    } else {
      assertThat(actualValue)
          .as(
              "variable "
                  + var1
                  + " not found in process instanceToContinue, available variables:"
                  + variables)
          .usingRecursiveComparison()
          .isEqualTo(value1);
    }
    return this;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }

  public ProcessInstanceAssert hasNotPassedElementWithId(String elementId) {
    List<FlowNodeInstanceDTO> bpmnElementState =
        bpmnTestEngine.getScopeWithElementId(processInstanceId, elementId);

    assertThat(
            bpmnElementState.isEmpty()
                || bpmnElementState.stream().allMatch(state -> state.getPassedCnt() == 0))
        .as("element with " + elementId + " not found in process instanceToContinue")
        .isTrue();
    return this;
  }

  public ProcessInstanceAssert hasVariableMatching(String var1, Consumer<Object> consumer) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceId);
    VariableValue variableValue = variables.get(var1);
    assertThat(variableValue).isNotNull();
    consumer.accept(Variables.toJavaObject(variableValue));
    return this;
  }

  public ProcessInstanceAssert hasCollectioneMatching(String var1, Consumer<List<?>> consumer) {
    VariablesDTO variables = bpmnTestEngine.getVariables(processInstanceId);
    VariableValue variableValue = variables.get(var1);
    assertThat(variableValue).isNotNull();
    Object collection = Variables.toJavaObject(variableValue);
    assertThat(collection).isInstanceOf(List.class);
    consumer.accept((List<?>) collection);
    return this;
  }

  public void hasState(ExecutionState processInstanceState) {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);

    assertThat(processInstance.getScope().getState()).isEqualTo(processInstanceState);
  }

  public ProcessInstanceAssert isAborted() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance.getScope().getState()).isEqualTo(ExecutionState.ABORTED);
    return this;
  }

  public ProcessInstanceAssert isIncident() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance.getIncidentInfo()).isNotNull();
    return this;
  }

  public ProcessInstanceAssert isIncidentWithMessage(String expectedMessage) {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance.getIncidentInfo()).isNotNull();
    assertThat(processInstance.getIncidentInfo().getMessage()).contains(expectedMessage);
    return this;
  }

  public ProcessInstanceAssert isIncidentWithErrorCode(String errorCode) {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance.getIncidentInfo()).isNotNull();
    assertThat(processInstance.getIncidentInfo().getMessage()).contains("Error code: " + errorCode);
    return this;
  }

  public ProcessInstanceAssert isStillActive() {
    ProcessInstanceDTO processInstance = bpmnTestEngine.getProcessInstance(processInstanceId);
    assertThat(processInstance.getScope().getState()).isEqualTo(ExecutionState.ACTIVE);
    return this;
  }
}
