/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowNodeStateEnum;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class CallActivityInstanceProcessor
    extends ActivityInstanceProcessor<
        CallActivity, CallActivityInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public CallActivityInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      CallActivityInstance callActivityInstance,
      String inputFlowId,
      VariableScope variables) {
    callActivityInstance.setState(FlowNodeStateEnum.ACTIVE);

    UUID newProcessInstanceId = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceId);
    CallActivity flowNode = callActivityInstance.getFlowNode();

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(flowNode.getCalledElement(), variables);
    if (jsonNode != null) {
      VariablesDTO commandVariables;
      if (callActivityInstance.getFlowNode().isPropagateAllParentVariables()) {
        commandVariables = variables.scopeAndParentsToDto();
      } else {
        commandVariables = variables.scopeToDTO();
      }

      processInstanceProcessingContext
          .getInstanceResult()
          .addNewStartCommand(
              new NewStartCommand(
                  newProcessInstanceId,
                  flowNode,
                  callActivityInstance,
                  jsonNode.asText(),
                  commandVariables,
                  callActivityInstance.getFlowNode().isPropagateAllChildVariables(),
                  callActivityInstance.getFlowNode().getIoMapping().getOutputMappings()));
    } else {
      callActivityInstance.setState(FlowNodeStateEnum.ABORTED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      CallActivityInstance instance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables) {
    instance.setState(FlowNodeStateEnum.COMPLETED);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      CallActivityInstance instance,
      VariableScope processInstanceVariables) {
    processInstanceProcessingContext
        .getInstanceResult()
        .addTerminateCommand(
            new TerminateTriggerDTO(instance.getChildProcessInstanceId(), List.of()));
  }
}
