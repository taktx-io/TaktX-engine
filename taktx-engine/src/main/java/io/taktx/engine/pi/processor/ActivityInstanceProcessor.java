/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowNodeStateEnum;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.BoundaryEventInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Set;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ActivityInstanceProcessor<
        E extends Activity, I extends ActivityInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FlowNodeInstanceProcessor<E, I, C> {

  protected FeelExpressionHandler feelExpressionHandler;

  protected ActivityInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected final void processStartSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables) {

    if (flownodeInstance.isIteration()) {
      variables.put("loopCnt", new IntNode(flownodeInstance.getLoopCnt()));
      variables.put(
          flownodeInstance.getFlowNode().getLoopCharacteristics().getInputElement(),
          flownodeInstance.getInputElement());
    }

    processStartSpecificActivityInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flownodeInstance,
        inputFlowId,
        variables);

    if (flownodeInstance.getState() == FlowNodeStateEnum.ACTIVE) {
      E flowNode = flownodeInstance.getFlowNode();
      flowNode
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(
                        flownodeInstance.getParentInstance(),
                        boundaryEvent,
                        flowNodeInstanceProcessingContext
                            .getFlowNodeInstances()
                            .nextElementInstanceId());
                boundaryEventInstance.setState(FlowNodeStateEnum.INITIAL);

                boundaryEventInstance.setAttachedInstanceId(
                    flownodeInstance.getElementInstanceId());
                flownodeInstance.addBoundaryEventId(boundaryEventInstance.getElementInstanceId());
                flowNodeInstanceProcessingContext
                    .getDirectInstanceResult()
                    .addNewFlowNodeInstance(
                        processInstanceProcessingContext.getProcessInstance(),
                        new FlowNodeInstanceInfo(boundaryEventInstance, null));
              });
    }

    handleFinishedIteration(flownodeInstance, variables);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flowNodeInstance,
      C trigger,
      VariableScope variables) {

    processContinueSpecificActivityInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        trigger,
        variables);

    if (flowNodeInstance.isDone()) {
      flowNodeInstance
          .getBoundaryEventIds()
          .forEach(
              id ->
                  flowNodeInstanceProcessingContext.getDirectInstanceResult().addAbortInstance(id));
    }

    handleFinishedIteration(flowNodeInstance, variables);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I instance,
      VariableScope currentVariableScope) {

    instance
        .getBoundaryEventIds()
        .forEach(
            id -> flowNodeInstanceProcessingContext.getDirectInstanceResult().addAbortInstance(id));

    processTerminateSpecificActivityInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        instance,
        currentVariableScope);
  }

  protected abstract void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flownodeInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables);

  protected abstract void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I externalTaskInstance,
      C trigger,
      VariableScope flowNodeInstanceVariables);

  protected abstract void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I instance,
      VariableScope variables);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    if (flowNodeInstance.isIteration()) {
      return Set.of();
    }
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  private void handleFinishedIteration(I flownodeInstance, VariableScope variables) {
    if (flownodeInstance.getState() == FlowNodeStateEnum.COMPLETED
        && flownodeInstance.isIteration()) {
      Activity flowNode = flownodeInstance.getFlowNode();
      String outputElement = flowNode.getLoopCharacteristics().getOutputElement();
      JsonNode jsonNode = feelExpressionHandler.processFeelExpression(outputElement, variables);
      flownodeInstance.setOutputElement(jsonNode);
    }
    variables.remove("loopCnt");
    variables.remove(flownodeInstance.getFlowNode().getLoopCharacteristics().getInputElement());
  }
}
