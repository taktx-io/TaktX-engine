/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.processor;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.AdHocSubProcess;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.ScopeProcessor;
import io.taktx.engine.pi.model.AdHocSubProcessInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.proto.VariableValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class AdHocSubProcessInstanceProcessor
    extends ActivityInstanceProcessor<
        SubProcess, AdHocSubProcessInstance, ContinueFlowElementTriggerDTO> {

  private ScopeProcessor scopeProcessor;

  @Inject
  public AdHocSubProcessInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ScopeProcessor scopeProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
    this.scopeProcessor = scopeProcessor;
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      AdHocSubProcessInstance adHocInstance,
      String inputFlowId) {

    Scope childScope = scope.selectChildScope(adHocInstance, adHocInstance.getFlowElements());
    adHocInstance.setScope(childScope);
    adHocInstance.setState(ExecutionState.ACTIVE);

    FlowElements subProcessElements = adHocInstance.getFlowNode().getElements();
    subProcessElements.getIndex().addAll(scope.getFlowElements().getIndex());

    List<String> elementIds = evaluateActiveElementsCollection(adHocInstance, variableScope);

    if (!elementIds.isEmpty()) {
      activateElements(elementIds, adHocInstance, childScope, variableScope, subProcessElements);
    }

    scopeProcessor.doBusiness(processInstanceProcessingContext, childScope, variableScope);
    scopeProcessor.bubbleUpEvents(scope, adHocInstance);
    adHocInstance.setState(childScope.getState());
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      AdHocSubProcessInstance adHocInstance,
      ContinueFlowElementTriggerDTO trigger) {

    Scope childScope = adHocInstance.getScope();
    AdHocSubProcess adHoc = adHocInstance.getFlowNode();

    if (adHoc.getCompletionCondition() != null
        && !adHocInstance.isCompletionConditionTriggered()
        && childScope.getActiveCnt() > 0) {

      VariableValue conditionResult =
          feelExpressionHandler.processFeelExpression(
              adHoc.getCompletionCondition(), variableScope);

      if (conditionResult == null
          || conditionResult.getKindCase() != VariableValue.KindCase.BOOL_VALUE) {
        throw new IllegalStateException(
            "completionCondition must evaluate to a boolean for ad-hoc subprocess "
                + adHocInstance.getFlowNode().getId()
                + ", got: "
                + (conditionResult == null ? "null" : conditionResult.getKindCase()));
      }
      boolean conditionMet = conditionResult.getBoolValue();

      if (conditionMet) {
        if (adHoc.isCancelRemainingInstances()) {
          // Abort active children individually — do NOT call processAbort on the scope itself,
          // because that would set scope.state = ABORTED and prevent canSelectNextNodeContinue()
          // from returning true (it requires COMPLETED, not ABORTED).
          childScope.getFlowNodeInstances().getAllInstances().values().stream()
              .filter(FlowNodeInstance::isActive)
              .forEach(inst -> childScope.getDirectInstanceResult().addAbortInstance(inst));
          scopeProcessor.doBusiness(processInstanceProcessingContext, childScope, variableScope);
          // After doBusiness: activeCnt == 0, scope.state == INITIALIZED
          // → scope.getState() == COMPLETED
        } else {
          adHocInstance.setCompletionConditionTriggered(true);
        }
      }
    }

    adHocInstance.setState(childScope.getState());
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      AdHocSubProcessInstance adHocInstance) {

    Scope childScope = adHocInstance.getScope();
    AbortTriggerDTO trigger =
        new AbortTriggerDTO(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            List.of());
    scopeProcessor.processAbort(
        processInstanceProcessingContext, childScope, variableScope, trigger);
  }

  private List<String> evaluateActiveElementsCollection(
      AdHocSubProcessInstance adHocInstance, VariableScope variableScope) {

    String expr = adHocInstance.getFlowNode().getActiveElementsCollection();
    if (expr == null) {
      throw new IllegalStateException(
          "activeElementsCollection is required for ad-hoc subprocess "
              + adHocInstance.getFlowNode().getId());
    }

    VariableValue result = feelExpressionHandler.processFeelExpression(expr, variableScope);

    if (result == null || result.getKindCase() != VariableValue.KindCase.LIST_VALUE) {
      throw new IllegalStateException(
          "activeElementsCollection must evaluate to a list for ad-hoc subprocess "
              + adHocInstance.getFlowNode().getId()
              + ", got: "
              + (result == null ? "null" : result.getKindCase()));
    }

    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < result.getListValue().getItemsCount(); i++) {
      VariableValue item = result.getListValue().getItems(i);
      if (item.getKindCase() != VariableValue.KindCase.STRING_VALUE) {
        throw new IllegalStateException(
            "Each element in activeElementsCollection must be a string for ad-hoc subprocess "
                + adHocInstance.getFlowNode().getId());
      }
      String id = item.getStringValue();
      if (!seen.add(id)) {
        throw new IllegalStateException(
            "Duplicate element id '"
                + id
                + "' in activeElementsCollection of ad-hoc subprocess "
                + adHocInstance.getFlowNode().getId());
      }
    }
    return new ArrayList<>(seen);
  }

  private void activateElements(
      List<String> elementIds,
      AdHocSubProcessInstance adHocInstance,
      Scope childScope,
      VariableScope variableScope,
      FlowElements subProcessElements) {

    for (String elementId : elementIds) {
      FlowNode flowNode =
          subProcessElements
              .getFlowNode(elementId)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Unknown element '"
                              + elementId
                              + "' referenced in activeElementsCollection of ad-hoc subprocess "
                              + adHocInstance.getFlowNode().getId()));

      var instance = flowNode.createAndStoreNewInstance(adHocInstance, childScope);
      VariableScope instanceVariableScope = variableScope.selectChildScope(instance);
      childScope
          .getDirectInstanceResult()
          .addNewFlowNodeInstance(
              new StartFlowNodeInstanceInfo(instance, null, instanceVariableScope));
    }
  }
}
