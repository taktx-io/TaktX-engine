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
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import lombok.Getter;

import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

public class MultiInstanceProcessor
    extends FlowNodeInstanceProcessor<
        Activity, MultiInstanceInstance, ContinueFlowElementTriggerDTO> {

  private final FeelExpressionHandler feelExpressionHandler;
  @Getter
  private final ActivityInstanceProcessor<?, ?, ?> processor;

  public MultiInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      ActivityInstanceProcessor<?, ?, ?> activityInstanceProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(activityInstanceProcessor.getIoMappingProcessor(), processInstanceMapper, clock);
    this.processor = activityInstanceProcessor;
    this.feelExpressionHandler = feelExpressionHandler;
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
          processInstanceProcessingContext,
              multiInstanceInstance,
          inputFlowId,
          iterationInstance);
      handleCompleted(scope, multiInstanceInstance);
    }
  }

  private void loopStartIterations(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      ActivityInstance<?> iterationInstance) {
    while (iterationInstance != null) {
      startIteration(
          processInstanceProcessingContext,
              iterationInstance,
          multiInstanceInstance,
          inputFlowId);
      if (iterationInstance.isActive()) {
        multiInstanceInstance.setState(ExecutionState.ACTIVE);
      }
      if (iterationInstance.getFlowNode().getLoopCharacteristics().isSequential()) {
        if (iterationInstance.getState() == ExecutionState.COMPLETED) {
          iterationInstance =
              getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
        } else {
          iterationInstance = null;
        }
      } else {
        iterationInstance =
            getNextIterationInstance(multiInstanceInstance.getScope(), iterationInstance);
      }
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

  private void startIteration(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      ActivityInstance<?> iterationInstance,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId) {

    processor.processStart(
        processInstanceProcessingContext, multiInstanceInstance.getScope(), iterationInstance, inputFlowId);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger) {

      long instanceId = trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel() + 1);

      ActivityInstance<?> iterationInstance =
              (ActivityInstance<?>) scope.getFlowNodeInstances().getInstanceWithInstanceId(instanceId);

      if (iterationInstance.getState() == ExecutionState.COMPLETED) {

          iterationInstance =
                  getNextIterationInstance(
                          multiInstanceInstance.getScope(),
                          iterationInstance);

          loopStartIterations(
                  processInstanceProcessingContext,
                  multiInstanceInstance,
                  trigger.getInputFlowId(),
                  iterationInstance);
      }

      handleCompleted(
              scope, multiInstanceInstance);
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
    multiInstanceInstance.getScope().updateActiveCountForInstances();
    if (multiInstanceInstance.getScope().getState().isDone()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      Map<Long, FlowNodeInstance<?>> allInstances = multiInstanceInstance.getScope().getFlowNodeInstances().getAllInstances();
      ;
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
        .forEach(
            iteration ->
                processor.processAbort(processInstanceProcessingContext, scope, iteration));
  }
}
