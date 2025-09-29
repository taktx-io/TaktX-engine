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
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.StoredScopeWrapper;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

public class MultiInstanceProcessor
    extends FlowNodeInstanceProcessor<
        Activity, MultiInstanceInstance, ContinueFlowElementTriggerDTO> {

  private final FeelExpressionHandler feelExpressionHandler;
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
      ProcessInstance processInstance,
      MultiInstanceInstance flowNodeInstance,
      Scope scope,
      VariableScope variables) {
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables) {

    Activity activity = multiInstanceInstance.getFlowNode();

    JsonNode inputCollection =
        feelExpressionHandler.processFeelExpression(
            activity.getLoopCharacteristics().getInputCollection(), flowNodeInstanceVariables);

    if (inputCollection == null || inputCollection.isEmpty()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
    } else {
      ActivityInstance<?> iterationInstance =
          prepareIterationInstances(
              activity, multiInstanceInstance, inputCollection, multiInstanceInstance.getScope());
      loopStartIterations(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          multiInstanceInstance,
          inputFlowId,
          iterationInstance,
          flowNodeInstanceVariables,
          activity);
      handleCompleted(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext.getFlowElements(),
          multiInstanceInstance,
          flowNodeInstanceVariables);
    }
  }

  private void loopStartIterations(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      ActivityInstance<?> iterationInstance,
      VariableScope scopeVariables,
      Activity activity) {
    while (iterationInstance != null) {
      startIteration(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          iterationInstance,
          multiInstanceInstance,
          inputFlowId,
          scopeVariables);
      if (iterationInstance.isActive()) {
        multiInstanceInstance.setState(ExecutionState.ACTIVE);
      }
      if (activity.getLoopCharacteristics().isSequential()) {
        if (iterationInstance.getState() == ExecutionState.COMPLETED) {
          iterationInstance =
              getNextIterationInstance(
                  processInstanceProcessingContext,
                  multiInstanceInstance.getScope(),
                  iterationInstance,
                  flowNodeInstanceProcessingContext.getFlowElements());
        } else {
          iterationInstance = null;
        }
      } else {
        iterationInstance =
            getNextIterationInstance(
                processInstanceProcessingContext,
                multiInstanceInstance.getScope(),
                iterationInstance,
                flowNodeInstanceProcessingContext.getFlowElements());
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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ActivityInstance<?> iterationInstance,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      VariableScope scopeVariables) {

    FlowNodeInstanceProcessingContext iterationFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            multiInstanceInstance.getScope(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            flowNodeInstanceProcessingContext.getSubProcessLevel(),
            flowNodeInstanceProcessingContext.getDirectInstanceResult());
    processor.processStart(
        processInstanceProcessingContext,
        iterationFlowNodeInstanceProcessingContext,
        iterationInstance,
        inputFlowId,
        scopeVariables);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {

    FlowElements subFlowElements = new FlowElements();
    subFlowElements
        .getIndex()
        .addAll(flowNodeInstanceProcessingContext.getFlowElements().getIndex());
    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    long instanceId =
        trigger
            .getElementInstanceIdPath()
            .get(flowNodeInstanceProcessingContext.getSubProcessLevel() + 1);

    StoredScopeWrapper storedScopeWrapper =
        new StoredScopeWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            multiInstanceInstance.getScope(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            processInstanceMapper);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>) storedScopeWrapper.getInstanceWithInstanceId(instanceId);

    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            multiInstanceInstance.getScope(),
            subFlowElements,
            flowNodeInstanceProcessingContext.getSubProcessLevel(),
            flowNodeInstanceProcessingContext.getDirectInstanceResult());
    processor.processContinue(
        processInstanceProcessingContext,
        subFlowNodeInstanceProcessingContext,
        iterationInstance,
        trigger,
        flowNodeInstanceVariables);
    if (iterationInstance.getState() == ExecutionState.COMPLETED) {

      iterationInstance =
          getNextIterationInstance(
              processInstanceProcessingContext,
              multiInstanceInstance.getScope(),
              iterationInstance,
              flowNodeInstanceProcessingContext.getFlowElements());

      loopStartIterations(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          multiInstanceInstance,
          trigger.getInputFlowId(),
          iterationInstance,
          flowNodeInstanceVariables,
          activity);
    }

    handleCompleted(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext.getFlowElements(),
        multiInstanceInstance,
        flowNodeInstanceVariables);
  }

  private ActivityInstance<?> getNextIterationInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements) {
    if (iterationInstance.getNextIterationId() >= 0) {
      StoredScopeWrapper storedScopeWrapper =
          new StoredScopeWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              scope,
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowElements,
              processInstanceMapper);
      return (ActivityInstance<?>)
          storedScopeWrapper.getInstanceWithInstanceId(iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private void handleCompleted(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      VariableScope scopeVariables) {
    multiInstanceInstance.getScope().updateActiveCountForInstances();
    if (multiInstanceInstance.getScope().getState().isDone()) {
      multiInstanceInstance.setState(ExecutionState.COMPLETED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      StoredScopeWrapper storedScopeWrapper =
          new StoredScopeWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              multiInstanceInstance.getScope(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowElements,
              processInstanceMapper);

      Map<Long, FlowNodeInstance<?>> allInstances = storedScopeWrapper.getAllInstances();
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
        scopeVariables.put(outputCollection, arrayNode);
      }
    }
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      MultiInstanceInstance instance,
      VariableScope currentVariableScope) {
    Scope scope = instance.getScope();
    scope
        .getInstances()
        .values()
        .forEach(
            iteration ->
                processor.processAbort(
                    processInstanceProcessingContext,
                    flowNodeInstanceProcessingContext,
                    iteration,
                    currentVariableScope));
  }
}
