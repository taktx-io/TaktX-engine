/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowConditionDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.GatewayInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
public abstract class GatewayInstanceProcessor<
        E extends Gateway, I extends GatewayInstance<E>, C extends ContinueFlowElementTriggerDTO>
    extends FlowNodeInstanceProcessor<E, I, C> {

  private FeelExpressionHandler feelExpressionHandler;

  protected GatewayInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
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
      I gatewayInstance,
      String inputFlowId) {
    processStartSpecificGatewayInstance(
        processInstanceProcessingContext, scope, gatewayInstance, inputFlowId);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      C trigger) {
    throw new IllegalStateException("We should never continue a gateway instance");
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I gatewayInstance,
      Scope scope,
      VariableScope variableScope) {
    Set<SequenceFlow> outgoingFlows = new HashSet<>();
    if (canTriggerOutputFlows(gatewayInstance, scope)) {
      gatewayInstance.resetFlows();
      E gatewayNode = gatewayInstance.getFlowNode();
      Set<SequenceFlow> sequenceFlows = gatewayNode.getOutGoingSequenceFlows();
      Set<SequenceFlow> flowsWithCondition =
          sequenceFlows.stream()
              .filter(sequenceFlow -> !FlowConditionDTO.NONE.equals(sequenceFlow.getCondition()))
              .collect(Collectors.toSet());
      Set<SequenceFlow> flowsWithMatchingCondition =
          flowsWithCondition.stream()
              .filter(
                  sequenceFlow -> {
                    com.fasterxml.jackson.databind.JsonNode result =
                        feelExpressionHandler.processFeelExpression(
                            sequenceFlow.getCondition().getExpression(), variableScope);
                    // Handle null FEEL expression results (e.g., missing variables, invalid
                    // expressions)
                    return result != null && result.asBoolean();
                  })
              .collect(Collectors.toSet());

      outgoingFlows.addAll(flowsWithMatchingCondition);
      if (outgoingFlows.isEmpty() && gatewayNode.getDefaultFlow() != null) {
        outgoingFlows.add(gatewayNode.getDefaultSequenceFlow());
      } else if (flowsWithCondition.isEmpty()) {
        // No conditions were set on the outgoing flows, so we select all of them
        outgoingFlows.addAll(sequenceFlows);
      } else {
        // No matching condition found, and no default flow is set. Do not add any flows
      }

      if (outgoingFlows.isEmpty()) {
        log.warn("No outgoing sequence flow selected for gateway {}", gatewayNode.getId());
        gatewayInstance.raiseIncident("No outgoing sequence flow selected for gateway");
      }
    }
    gatewayInstance.setSelectedOutputFlows(
        outgoingFlows.stream().map(SequenceFlow::getId).collect(Collectors.toSet()));
    return outgoingFlows;
  }

  protected abstract boolean canTriggerOutputFlows(I gatewayInstance, Scope scope);

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {
    processTerminateSpecificGatewayInstance(
        processInstanceProcessingContext.getInstanceResult(),
        scope.getDirectInstanceResult(),
        instance);
  }

  protected abstract void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flownodeInstance,
      String inputFlowId);

  protected abstract void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult, DirectInstanceResult directInstanceResult, I instance);
}
