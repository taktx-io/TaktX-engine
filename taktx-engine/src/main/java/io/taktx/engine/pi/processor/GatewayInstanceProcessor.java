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

import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.dto.v_1_0_0.FlowConditionDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Gateway;
import io.taktx.engine.pd.model.SequenceFlow;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.GatewayInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@NoArgsConstructor
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
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I gatewayInstance,
      String inputFlowId,
      VariableScope variables) {
    processStartSpecificGatewayInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        gatewayInstance,
        inputFlowId,
        variables);
  }

  @Override
  protected final void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      I flowNodeInstance,
      C trigger,
      VariableScope processInstanceVariables) {
    // Should never happen
  }

  @Override
  protected Set<SequenceFlow> getSelectedSequenceFlows(
      ProcessInstance processInstance,
      I gatewayInstance,
      FlowNodeInstances flowNodeInstances,
      VariableScope variables) {
    Set<SequenceFlow> outgoingFlows = new HashSet<>();
    if (canTriggerOutputFlows(gatewayInstance, flowNodeInstances)) {
      gatewayInstance.resetFlows();
      E gatewayNode = gatewayInstance.getFlowNode();
      Set<SequenceFlow> sequenceFlows = gatewayNode.getOutGoingSequenceFlows();
      Set<SequenceFlow> flowsWithCondition =
          sequenceFlows.stream()
              .filter(sequenceFlow -> !FlowConditionDTO.NONE.equals(sequenceFlow.getCondition()))
              .filter(
                  sequenceFlow ->
                      feelExpressionHandler
                          .processFeelExpression(
                              sequenceFlow.getCondition().getExpression(), variables)
                          .asBoolean())
              .collect(Collectors.toSet());

      outgoingFlows.addAll(flowsWithCondition);
      if (outgoingFlows.isEmpty() && gatewayNode.getDefaultFlow() != null) {
        outgoingFlows.add(gatewayNode.getDefaultSequenceFlow());
      } else if (outgoingFlows.isEmpty() && !sequenceFlows.isEmpty()) {
        // Last chance, if no condition is met and no default flow is set, take the outgoing flows
        outgoingFlows.addAll(sequenceFlows);
      }
      if (outgoingFlows.isEmpty()) {
        throw new ProcessInstanceException(
            processInstance,
            gatewayInstance,
            "No outgoing flow could be selected found for exclusive gateway: "
                + gatewayNode.getId());
      }
    }
    gatewayInstance.setSelectedOutputFlows(
        outgoingFlows.stream().map(SequenceFlow::getId).collect(Collectors.toSet()));
    return outgoingFlows;
  }

  protected abstract boolean canTriggerOutputFlows(
      I gatewayInstance, FlowNodeInstances flowNodeInstances);

  @Override
  protected void processTerminateSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I instance,
      VariableScope currentVariableScope) {
    processTerminateSpecificGatewayInstance(
        processInstanceProcessingContext.getInstanceResult(),
        flowNodeInstanceProcessingContext.getDirectInstanceResult(),
        instance);
  }

  protected abstract void processStartSpecificGatewayInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flownodeInstance,
      String inputFlowId,
      VariableScope variables);

  protected abstract void processTerminateSpecificGatewayInstance(
      InstanceResult instanceResult, DirectInstanceResult directInstanceResult, I instance);
}
