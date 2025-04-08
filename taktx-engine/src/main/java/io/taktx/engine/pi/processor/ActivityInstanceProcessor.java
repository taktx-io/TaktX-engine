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
import com.fasterxml.jackson.databind.node.IntNode;
import io.taktx.dto.v_1_0_0.ActtivityStateEnum;
import io.taktx.dto.v_1_0_0.CatchEventStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
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
      ProcessingContext processingContext,
      FlowNodeInstances flowNodeInstances,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
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
        processingContext,
        directInstanceResult,
        flowElements,
        flownodeInstance,
        inputFlowId,
        variables);

    if (flownodeInstance.getState() == ActtivityStateEnum.WAITING) {
      E flowNode = flownodeInstance.getFlowNode();
      flowNode
          .getBoundaryEvents()
          .forEach(
              boundaryEvent -> {
                BoundaryEventInstance boundaryEventInstance =
                    new BoundaryEventInstance(
                        flownodeInstance.getParentInstance(),
                        boundaryEvent,
                        flowNodeInstances.nextElementInstanceId());
                boundaryEventInstance.setState(CatchEventStateEnum.INITIAL);

                boundaryEventInstance.setAttachedInstanceId(
                    flownodeInstance.getElementInstanceId());
                flownodeInstance.addBoundaryEventId(boundaryEventInstance.getElementInstanceId());
                directInstanceResult.addNewFlowNodeInstance(
                    processingContext.getProcessInstance(),
                    new FlowNodeInstanceInfo(boundaryEventInstance, null));
              });
    }

    handleFinishedIteration(flownodeInstance, variables);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      I flowNodeInstance,
      C trigger,
      VariableScope variables,
      FlowNodeInstances flowNodeInstances) {

    processContinueSpecificActivityInstance(
        processingContext,
        directInstanceResult,
        subProcessLevel,
        flowElements,
        flowNodeInstance,
        trigger,
        variables);

    if (flowNodeInstance.isCompleted()) {
      flowNodeInstance.getBoundaryEventIds().forEach(directInstanceResult::addTerminateInstance);
    }

    handleFinishedIteration(flowNodeInstance, variables);
  }

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      I instance,
      VariableScope currentVariableScope,
      FlowElements flowElements) {

    instance.getBoundaryEventIds().forEach(directInstanceResult::addTerminateInstance);

    processTerminateSpecificActivityInstance(
        processingContext, directInstanceResult, instance, currentVariableScope);
  }

  protected abstract void processStartSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flownodeInstance,
      String inputFlowId,
      VariableScope flowNodeInstanceVariables);

  protected abstract void processContinueSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      I externalTaskInstance,
      C trigger,
      VariableScope flowNodeInstanceVariables);

  protected abstract void processTerminateSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
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
    if (flownodeInstance.getState() == ActtivityStateEnum.FINISHED
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
