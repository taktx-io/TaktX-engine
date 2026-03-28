/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ScopeProcessor;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import lombok.Getter;

public class MultiInstanceProcessor
    extends FlowNodeInstanceProcessor<
        Activity, MultiInstanceInstance, ContinueFlowElementTriggerDTO> {

  private final FeelExpressionHandler feelExpressionHandler;
  private final ScopeProcessor scopeProcessor;

  @Getter private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      ActivityInstanceProcessor<?, ?, ?> activityInstanceProcessor,
      ProcessInstanceMapper processInstanceMapper,
      ScopeProcessor scopeProcessor,
      Clock clock) {
    super(activityInstanceProcessor.getIoMappingProcessor(), processInstanceMapper, clock);
    this.processor = activityInstanceProcessor;
    this.feelExpressionHandler = feelExpressionHandler;
    this.scopeProcessor = scopeProcessor;
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      MultiInstanceInstance flowNodeInstance,
      Scope scope,
      VariableScope variableScope) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId) {

    Scope subScope = scope.selectChildScope(multiInstanceInstance, scope.getFlowElements());
    multiInstanceInstance.setScope(subScope);
    multiInstanceInstance.setState(ExecutionState.ACTIVE);

    Activity activity = multiInstanceInstance.getFlowNode();

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), variableScope);

    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
    } else {
      ActivityInstance<?> iterationInstance =
          prepareIterationInstances(
              activity, multiInstanceInstance, inputCollection, multiInstanceInstance.getScope());
      loopStartIterations(
          processInstanceProcessingContext,
          multiInstanceInstance,
          inputFlowId,
          variableScope,
          iterationInstance);
      handleCompleted(variableScope, multiInstanceInstance);
    }
  }

  private void loopStartIterations(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      VariableScope variableScope,
      ActivityInstance<?> iterationInstance) {
    if (iterationInstance.getFlowNode().getLoopCharacteristics().isSequential()) {
      while (iterationInstance != null) {
        startSingleIteration(
            processInstanceProcessingContext,
            iterationInstance,
            multiInstanceInstance,
            variableScope,
            inputFlowId);
        if (iterationInstance.getState() == ExecutionState.COMPLETED) {
          iterationInstance =
              getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
        } else {
          iterationInstance = null;
        }
      }
    } else {
      while (iterationInstance != null) {
        VariableScope childVariableScope = variableScope.selectChildScope(iterationInstance);
        StartFlowNodeInstanceInfo newFlowNodeInstanceInfo =
            new StartFlowNodeInstanceInfo(iterationInstance, null, childVariableScope);
        multiInstanceInstance
            .getScope()
            .getDirectInstanceResult()
            .addNewFlowNodeInstance(newFlowNodeInstanceInfo);
        iterationInstance =
            getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
      }
      scopeProcessor.doBusiness(
          processInstanceProcessingContext, multiInstanceInstance.getScope(), variableScope);
    }
  }

  private ActivityInstance<?> prepareIterationInstances(
      Activity activity,
      MultiInstanceInstance multiInstanceInstance,
      JsonNode inputCollection,
      Scope scope) {
    ActivityInstance<?> previousIterationInstance = null;
    ActivityInstance<?> firstInstance = null;
    for (int i = 0; i < inputCollection.size(); i++) {
      ActivityInstance<?> iterationInstance =
          activity.newActivityInstance(multiInstanceInstance, scope.nextElementInstanceId());
      iterationInstance.setState(ExecutionState.INITIALIZED);
      iterationInstance.setIteration(true);
      iterationInstance.setLoopCnt(i);

      if (firstInstance == null) {
        firstInstance = iterationInstance;
      }

      if (previousIterationInstance != null) {
        previousIterationInstance.setNextIterationId(iterationInstance.getElementInstanceId());
      }

      previousIterationInstance = iterationInstance;
      iterationInstance.setLoopCnt(i);
      multiInstanceInstance.getScope().putInstance(iterationInstance);
      JsonNode inputElement = inputCollection.get(i);
      iterationInstance.setInputElement(inputElement);
    }
    return firstInstance;
  }

  private void startSingleIteration(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ActivityInstance<?> iterationInstance,
      MultiInstanceInstance multiInstanceInstance,
      VariableScope variableScope,
      String inputFlowId) {
    VariableScope childVariableScope = variableScope.selectChildScope(iterationInstance);
    StartFlowNodeInstanceInfo newFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(iterationInstance, null, childVariableScope);
    multiInstanceInstance
        .getScope()
        .getDirectInstanceResult()
        .addNewFlowNodeInstance(newFlowNodeInstanceInfo);
    scopeProcessor.doBusiness(
        processInstanceProcessingContext, multiInstanceInstance.getScope(), variableScope);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger) {

    long instanceId = trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel() + 1);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>)
            multiInstanceInstance
                .getScope()
                .getFlowNodeInstances()
                .getInstanceWithInstanceId(instanceId);

    if (iterationInstance.getState() == ExecutionState.COMPLETED
        && multiInstanceInstance.getFlowNode().getLoopCharacteristics().isSequential()) {
      iterationInstance =
          getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
      if (iterationInstance != null) {
        loopStartIterations(
            processInstanceProcessingContext,
            multiInstanceInstance,
            trigger.getInputFlowId(),
            variableScope,
            iterationInstance);
      }
    }

    handleCompleted(variableScope, multiInstanceInstance);
  }

  private ActivityInstance<?> getNextIterationInstance(
      Scope scope, ActivityInstance<?> iterationInstance) {
    if (iterationInstance.getNextIterationId() >= 0) {
      return (ActivityInstance<?>)
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private void handleCompleted(
      VariableScope variableScope, MultiInstanceInstance multiInstanceInstance) {
    if (multiInstanceInstance.getScope().getState().isDone()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      Map<Long, FlowNodeInstance<?>> allInstances =
          multiInstanceInstance.getScope().getFlowNodeInstances().getAllInstances();

      allInstances.values().stream()
          .filter(flowNodeInstance -> flowNodeInstance instanceof ActivityInstance<?>)
          .map(flowNodeInstance -> (ActivityInstance<?>) flowNodeInstance)
          .sorted(
              Comparator.comparingInt(
                  (ToIntFunction<ActivityInstance<?>>) ActivityInstance::getLoopCnt))
          .map(ActivityInstance::getOutputElement)
          .forEach(arrayNode::add);
      String outputCollection =
          multiInstanceInstance.getFlowNode().getLoopCharacteristics().getOutputCollection();
      if (outputCollection != null) {
        variableScope.put(outputCollection, arrayNode);
      }
    }
  }

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      MultiInstanceInstance instance) {
    scope
        .getFlowNodeInstances()
        .getAllInstances()
        .values()
        .forEach(iteration -> scope.getDirectInstanceResult().addAbortInstance(iteration));
  }
}
