/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
      ProcessInstance processInstance, MultiInstanceInstance flowNodeInstance, Scope scope) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId) {

    Scope subScope = scope.selectChildScope(multiInstanceInstance, scope.getFlowElements());
    multiInstanceInstance.setScope(subScope);
    multiInstanceInstance.setState(ExecutionState.ACTIVE);

    Activity activity = multiInstanceInstance.getFlowNode();

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), scope.getVariableScope());

    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
    } else {
      ActivityInstance<?> iterationInstance =
          prepareIterationInstances(
              activity, multiInstanceInstance, inputCollection, multiInstanceInstance.getScope());
      loopStartIterations(
          processInstanceProcessingContext, multiInstanceInstance, inputFlowId, iterationInstance);
      handleCompleted(scope, multiInstanceInstance);
    }
  }

  private void loopStartIterations(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      ActivityInstance<?> iterationInstance) {
    if (iterationInstance.getFlowNode().getLoopCharacteristics().isSequential()) {
      while (iterationInstance != null) {
        startSingleIteration(
            processInstanceProcessingContext,
            iterationInstance,
            multiInstanceInstance,
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
        StartFlowNodeInstanceInfo newFlowNodeInstanceInfo =
            new StartFlowNodeInstanceInfo(iterationInstance, null);
        multiInstanceInstance
            .getScope()
            .getDirectInstanceResult()
            .addNewFlowNodeInstance(
                processInstanceProcessingContext.getProcessInstance(), newFlowNodeInstanceInfo);
        iterationInstance =
            getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
      }
      scopeProcessor.doBusiness(processInstanceProcessingContext, multiInstanceInstance.getScope());
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
      String inputFlowId) {
    StartFlowNodeInstanceInfo newFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(iterationInstance, null);
    multiInstanceInstance
        .getScope()
        .getDirectInstanceResult()
        .addNewFlowNodeInstance(
            processInstanceProcessingContext.getProcessInstance(), newFlowNodeInstanceInfo);
    scopeProcessor.doBusiness(processInstanceProcessingContext, multiInstanceInstance.getScope());
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger) {

    long instanceId = trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel() + 1);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>)
            multiInstanceInstance
                .getScope()
                .getFlowNodeInstances()
                .getInstanceWithInstanceId(instanceId);

    if (iterationInstance.getState() == ExecutionState.COMPLETED) {

      // Only for sequential multi instance, continue with starting next iteration
      if (multiInstanceInstance.getFlowNode().getLoopCharacteristics().isSequential()) {
        iterationInstance =
            getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
        if (iterationInstance != null) {
          loopStartIterations(
              processInstanceProcessingContext,
              multiInstanceInstance,
              trigger.getInputFlowId(),
              iterationInstance);
        }
      }
    }

    handleCompleted(scope, multiInstanceInstance);
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

  private void handleCompleted(Scope scope, MultiInstanceInstance multiInstanceInstance) {
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
        scope.getVariableScope().put(outputCollection, arrayNode);
      }
    }
  }

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      MultiInstanceInstance instance) {
    scope
        .getFlowNodeInstances()
        .getAllInstances()
        .values()
        .forEach(iteration -> scope.getDirectInstanceResult().addAbortInstance(iteration));
  }
}
