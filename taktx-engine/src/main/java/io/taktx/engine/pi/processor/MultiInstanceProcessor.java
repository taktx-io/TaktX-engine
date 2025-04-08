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
import io.taktx.dto.v_1_0_0.ActtivityStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
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
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
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
          processingContext,
          directInstanceResult,
          flowElements,
          multiInstanceInstance,
          inputFlowId,
          iterationInstance,
          flowNodeInstanceVariables,
          activity);
      handleCompleted(
          processingContext, flowElements, multiInstanceInstance, flowNodeInstanceVariables);
    }
  }

  private void loopStartIterations(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      ActivityInstance<?> iterationInstance,
      VariableScope flowNodeInstancesVariables,
      Activity activity) {
    while (iterationInstance != null) {
      startIteration(
          processingContext,
          iterationInstance,
          flowElements,
          multiInstanceInstance,
          inputFlowId,
          flowNodeInstancesVariables,
          directInstanceResult);
      if (iterationInstance.isAwaiting()) {
        multiInstanceInstance.setState(ActtivityStateEnum.WAITING);
      }
      if (activity.getLoopCharacteristics().isSequential()) {
        if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {
          iterationInstance =
              getNextIterationInstance(
                  processingContext,
                  multiInstanceInstance.getFlowNodeInstances(),
                  iterationInstance,
                  flowElements);
        } else {
          iterationInstance = null;
        }
      } else {
        iterationInstance =
            getNextIterationInstance(
                processingContext,
                multiInstanceInstance.getFlowNodeInstances(),
                iterationInstance,
                flowElements);
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

  private ActivityInstance<?> startIteration(
      ProcessingContext processingContext,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      String inputFlowId,
      VariableScope flowNodeInstancesVariables,
      DirectInstanceResult directInstanceResult) {

    processor.processStart(
        processingContext,
        directInstanceResult,
        flowElements,
        iterationInstance,
        inputFlowId,
        flowNodeInstancesVariables,
        multiInstanceInstance.getFlowNodeInstances());

    return iterationInstance;
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope flowNodeInstanceVariables,
      FlowNodeInstances flowNodeInstances) {
    subProcessLevel++;

    FlowElements subFlowElements = new FlowElements();
    subFlowElements.getIndex().addAll(flowElements.getIndex());
    Activity activity = multiInstanceInstance.getFlowNode();
    subFlowElements.addFlowElement(activity);

    long instanceId = trigger.getElementInstanceIdPath().get(subProcessLevel);

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processingContext.getProcessInstance().getProcessInstanceKey(),
            multiInstanceInstance.getFlowNodeInstances(),
            processingContext.getFlowNodeInstanceStore(),
            flowElements);

    ActivityInstance<?> iterationInstance =
        (ActivityInstance<?>) storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(instanceId);

    processor.processContinue(
        processingContext,
        directInstanceResult,
        subProcessLevel,
        subFlowElements,
        iterationInstance,
        trigger,
        flowNodeInstanceVariables,
        multiInstanceInstance.getFlowNodeInstances());
    if (iterationInstance.getState() == ActtivityStateEnum.FINISHED) {

      iterationInstance =
          getNextIterationInstance(
              processingContext,
              multiInstanceInstance.getFlowNodeInstances(),
              iterationInstance,
              flowElements);

      loopStartIterations(
          processingContext,
          directInstanceResult,
          flowElements,
          multiInstanceInstance,
          trigger.getInputFlowId(),
          iterationInstance,
          flowNodeInstanceVariables,
          activity);
    }

    handleCompleted(
        processingContext, flowElements, multiInstanceInstance, flowNodeInstanceVariables);
  }

  private ActivityInstance<?> getNextIterationInstance(
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      ActivityInstance<?> iterationInstance,
      FlowElements flowElements) {
    if (iterationInstance.getNextIterationId() >= 0) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processingContext.getProcessInstance().getProcessInstanceKey(),
              flowNodeInstances,
              processingContext.getFlowNodeInstanceStore(),
              flowElements);
      return (ActivityInstance<?>)
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              iterationInstance.getNextIterationId());
    } else {
      return null;
    }
  }

  private static void handleCompleted(
      ProcessingContext processingContext,
      FlowElements flowElements,
      MultiInstanceInstance multiInstanceInstance,
      VariableScope flowNodeInstancesVariables) {
    multiInstanceInstance.getFlowNodeInstances().determineImplicitCompletedState();
    if (multiInstanceInstance.getFlowNodeInstances().getState().isFinished()) {
      multiInstanceInstance.setState(ActtivityStateEnum.FINISHED);
      ArrayNode arrayNode = new ObjectMapper().createArrayNode();

      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processingContext.getProcessInstance().getProcessInstanceKey(),
              multiInstanceInstance.getFlowNodeInstances(),
              processingContext.getFlowNodeInstanceStore(),
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      MultiInstanceInstance instance,
      VariableScope currentVariableScope,
      FlowElements flowElements) {
    FlowNodeInstances flowNodeInstances = instance.getFlowNodeInstances();
    flowNodeInstances
        .getInstances()
        .values()
        .forEach(
            iteration ->
                processor.processTerminate(
                    processingContext,
                    directInstanceResult,
                    iteration,
                    currentVariableScope,
                    flowNodeInstances,
                    flowElements));
  }
}
