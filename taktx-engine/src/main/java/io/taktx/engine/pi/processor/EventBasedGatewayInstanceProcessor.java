/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventBasedGateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class EventBasedGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        EventBasedGateway, EventBasedGatewayInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public EventBasedGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Override
  protected boolean canTriggerOutputFlows(EventBasedGatewayInstance gatewayInstance, Scope scope) {
    return true;
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      EventBasedGatewayInstance gatewayInstance,
      Scope scope,
      VariableScope variableScope) {
    return gatewayInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      EventBasedGatewayInstance eventBasedGatewayInstance,
      Scope scope,
      VariableScope variableScope) {
    if (eventBasedGatewayInstance.canSelectNextNodeStart()) {
      List<Long> flowNodeInstances =
          processNodeResultAndSelectNextInstance(
                  processInstance, eventBasedGatewayInstance, scope, variableScope)
              .stream()
              .map(FlowNodeInstance::getElementInstanceId)
              .toList();
      eventBasedGatewayInstance.setConnectedFlowNodeInstanceIds(flowNodeInstances);
    }
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      EventBasedGatewayInstance flownodeInstance,
      String inputFlowId) {
    // Nothing to do
  }

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      EventBasedGatewayInstance instance) {
    // Nothing to do
  }
}
