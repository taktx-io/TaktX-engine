/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.StoredFlowNodeInstancesWrapper;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.MultiInstanceInstance;
import io.taktx.engine.pi.model.ProcessInstance;
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
      FlowNodeInstances flowNodeInstances,
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
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
    } else {
      ActivityInstance<?> iterationInstance =
          prepareIterationInstances(
              activity,
              multiInstanceInstance,
              inputCollection,
              multiInstanceInstance.getFlowNodeInstances());
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
      VariableScope flowNodeInstancesVariables,
      Activity activity) {
    while (iterationInstance != null) {
      startIteration(
          processInstanceProcessingContext,
          flowNodeInstanceProcessingContext,
          iterationInstance,
          multiInstanceInstance,
          inputFlowId,
          flowNodeInstancesVariables);
      if (iterationInstance.isAwaiting()) {
        multiInstanceInstance.setState(ActtivityStateEnum.WAITING);
      }
      if (activity.getLoopCharacteristics().isSequential()) {
        if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {
          iterationInstance =
              getNextIterationInstance(
                  processInstanceProcessingContext,
                  multiInstanceInstance.getFlowNodeInstances(),
                  iterationInstance,
                  flowNodeInstanceProcessingContext.getFlowElements());
        } else {
          iterationInstance = null;
        }
      } else {
        iterationInstance =
            getNextIterationInstance(
                processInstanceProcessingContext,
                multiInstanceInstance.getFlowNodeInstances(),
                iterationInstance,
                flowNodeInstanceProcessingContext.getFlowElements());
      }
    }
  }

  private ActivityInstance<?> prepareIterationInstances(
      Activity activity,
      MultiInstanceInstance multiInstanceInstance,
      JsonNode inputCollection,
      FlowNodeInstances flowNodeInstances) {
    ActivityInstance<?> previousIterationInstance = null;
    ActivityInstance<?> firstInstance = null;
    for (int i = 0; i < inputCollection.size(); i++) {
      ActivityInstance<?> iterationInstance =
          activity.newActivityInstance(
              multiInstanceInstance, flowNodeInstances.nextElementInstanceId());
      iterationInstance.setState(ActtivityStateEnum.INITIAL);
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
      multiInstanceInstance.getFlowNodeInstances().putInstance(iterationInstance);
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
      VariableScope flowNodeInstancesVariables) {

    FlowNodeInstanceProcessingContext iterationFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            multiInstanceInstance.getFlowNodeInstances(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            flowNodeInstanceProcessingContext.getDirectInstanceResult());
    processor.processStart(
        processInstanceProcessingContext,
        iterationFlowNodeInstanceProcessingContext,
        iterationInstance,
        inputFlowId,
        flowNodeInstancesVariables);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables) {
    subProcessLevel++;

    FlowElements subFlowElements = new FlowElements();
    subFlowElements
        .getIndex()
        .addAll(flowNodeInstanceProcessingContext.getFlowElements().getIndex());
    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    long instanceId = trigger.getElementInstanceIdPath().get(subProcessLevel);

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            multiInstanceInstance.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements());

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>) storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(instanceId);

    FlowNodeInstanceProcessingContext subFlowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(
            multiInstanceInstance.getFlowNodeInstances(),
            subFlowElements,
            flowNodeInstanceProcessingContext.getDirectInstanceResult());
    processor.processContinue(
        processInstanceProcessingContext,
        subFlowNodeInstanceProcessingContext,
        subProcessLevel,
        iterationInstance,
        trigger,
        flowNodeInstanceVariables);
    if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {

      iterationInstance =
          getNextIterationInstance(
              processInstanceProcessingContext,
              multiInstanceInstance.getFlowNodeInstances(),
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
      FlowNodeInstances flowNodeInstances,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements) {
    if (iterationInstance.getNextIterationId() >= 0) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstances,
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowElements);
      return (ActivityInstance<?>)
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private static void handleCompleted(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      VariableScope flowNodeInstancesVariables) {
    multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();
    if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
              multiInstanceInstance.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowElements);

      Map<Long, FlowNodeInstance<?>> allInstances =
          storedFlowNodeInstancesWrapper.getAllInstances();
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
        flowNodeInstancesVariables.put(outputCollection, arrayNode);
      }
    }
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      MultiInstanceInstance instance,
      VariableScope currentVariableScope) {
    FlowNodeInstances flowNodeInstances = instance.getFlowNodeInstances();
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            iteration ->
                processor.processTerminate(
                    processInstanceProcessingContext,
                    flowNodeInstanceProcessingContext,
                    iteration,
                    currentVariableScope));
  }
}
