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
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
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
      Scope scope,
      VariableScope variableScope,
      I flownodeInstance,
      String inputFlowId) {

    if (flownodeInstance.isIteration()) {
      variableScope.put("loopCnt", new IntNode(flownodeInstance.getLoopCnt()));
      variableScope.put(
          flownodeInstance.getFlowNode().getLoopCharacteristics().getInputElement(),
          flownodeInstance.getInputElement());
    }

    Activity activity = flownodeInstance.getFlowNode();
    activity
        .getBoundaryEvents()
        .forEach(
            boundaryEvent ->
                scope
                    .getSubscriptions()
                    .addSubscriptionsForBoundaryEventDefinitions(boundaryEvent, flownodeInstance));

    processStartSpecificActivityInstance(
        processInstanceProcessingContext, scope, variableScope, flownodeInstance, inputFlowId);

    handleFinishedIteration(flownodeInstance, variableScope);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      C trigger) {

    processContinueSpecificActivityInstance(
        processInstanceProcessingContext, scope, variableScope, flowNodeInstance, trigger);

    handleFinishedIteration(flowNodeInstance, variableScope);
  }

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {

    scope.getSubscriptions().cancelSubscriptionsForInstance(instance, scope);

    processAbortSpecificActivityInstance(
        processInstanceProcessingContext, scope, variableScope, instance);
  }

  protected abstract void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flownodeInstance,
      String inputFlowId);

  protected abstract void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I externalTaskInstance,
      C trigger);

  protected abstract void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance);

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I flowNodeInstance,
      Scope scope,
      VariableScope variableScope) {
    if (flowNodeInstance.isIteration()) {
      return Set.of();
    }
    return flowNodeInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  private void handleFinishedIteration(I flownodeInstance, VariableScope variableScope) {
    if (flownodeInstance.getState() == ExecutionState.COMPLETED && flownodeInstance.isIteration()) {
      Activity flowNode = flownodeInstance.getFlowNode();
      // Handle null loop characteristics
      if (flowNode.getLoopCharacteristics() != null) {
        String outputElement = flowNode.getLoopCharacteristics().getOutputElement();
        if (outputElement != null) {
          JsonNode jsonNode =
              feelExpressionHandler.processFeelExpression(outputElement, variableScope);
          // Only set output element if FEEL expression returns a non-null result
          if (jsonNode != null) {
            flownodeInstance.setOutputElement(jsonNode);
          }
        }
      }
    }
  }
}
