/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
      ProcessInstance processInstance, EventBasedGatewayInstance gatewayInstance, Scope scope) {
    return gatewayInstance.getFlowNode().getOutGoingSequenceFlows();
  }

  @Override
  protected void selectNextNodeIfAllowedStart(
      ProcessInstance processInstance,
      EventBasedGatewayInstance eventBasedGatewayInstance,
      Scope scope) {
    if (eventBasedGatewayInstance.canSelectNextNodeStart()) {
      List<Long> flowNodeInstances =
          processNodeResultAndSelectNextInstance(processInstance, eventBasedGatewayInstance, scope)
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
