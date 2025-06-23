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
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.InclusiveGateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.InclusiveGatewayInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class InclusiveGatewayInstanceProcessor
    extends GatewayInstanceProcessor<
        InclusiveGateway, InclusiveGatewayInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public InclusiveGatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, feelExpressionHandler, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      InclusiveGatewayInstance gatewayInstance,
      String inputFlowId,
      VariableScope variables) {
    gatewayInstance.addTriggeredInputFlow(inputFlowId);
  }

  @Override
  protected void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      InclusiveGatewayInstance instance) {
    // Nothing to do
  }

  @Override
  protected boolean canTriggerOutputFlows(
      InclusiveGatewayInstance gatewayInstance, FlowNodeInstances flowNodeInstances) {
    // For each incoming flow check if the corresponding output flows have been triggered,
    // if any of them hasnt, we will not trigger the output flows
    // If there are no corresponding gateways, we assume we are a diverging and allow the flow to
    // trigger the output flows.

    Map<SequenceFlow, Set<InclusiveGatewayInstance>> previousTriggeredInstancePairs =
        findPreviousInclusiveGatewayInstances(
            gatewayInstance.getFlowNode().getIncomingSequenceFlows(), flowNodeInstances);
    Set<String> collect =
        previousTriggeredInstancePairs.keySet().stream()
            .map(SequenceFlow::getId)
            .collect(Collectors.toSet());
    if (previousTriggeredInstancePairs.isEmpty()) {
      return true;
    } else {
      return gatewayInstance.getTriggeredInputFlows().containsAll(collect);
    }
  }

  private Map<SequenceFlow, Set<InclusiveGatewayInstance>> findPreviousInclusiveGatewayInstances(
      Set<SequenceFlow> incomingSequenceFlows, FlowNodeInstances flowNodeInstances) {
    Map<SequenceFlow, Set<InclusiveGatewayInstance>> instanceMap = new HashMap<>();
    for (SequenceFlow incomingSequenceFlow : incomingSequenceFlows) {
      FlowNode sourceNode = incomingSequenceFlow.getSourceNode();
      if (sourceNode instanceof InclusiveGateway inclusiveGateway) {
        Optional<FlowNodeInstance<?>> instanceWithFlowNode =
            flowNodeInstances.getInstanceWithFlowNode(inclusiveGateway);
        if (instanceWithFlowNode.isPresent()) {
          InclusiveGatewayInstance gatewayInstance =
              (InclusiveGatewayInstance) instanceWithFlowNode.get();
          if (gatewayInstance.getSelectedOutputFlows().contains(incomingSequenceFlow.getId())) {
            instanceMap
                .computeIfAbsent(incomingSequenceFlow, k -> new HashSet<>())
                .add(gatewayInstance);
          }
        }
      } else {
        Map<SequenceFlow, Set<InclusiveGatewayInstance>> previousInclusiveGatewayInstances =
            findPreviousInclusiveGatewayInstances(
                sourceNode.getIncomingSequenceFlows(), flowNodeInstances);
        if (!previousInclusiveGatewayInstances.isEmpty()) {
          instanceMap
              .computeIfAbsent(incomingSequenceFlow, k -> new HashSet<>())
              .addAll(
                  previousInclusiveGatewayInstances.values().stream()
                      .flatMap(Set::stream)
                      .toList());
        }
      }
    }
    return instanceMap;
  }
}
