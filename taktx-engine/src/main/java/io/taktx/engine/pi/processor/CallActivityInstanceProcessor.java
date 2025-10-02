/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.Scope;
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
      Scope scope,
      CallActivityInstance callActivityInstance,
      String inputFlowId) {
    callActivityInstance.setState(ExecutionState.ACTIVE);

    UUID newProcessInstanceId = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceId);
    CallActivity flowNode = callActivityInstance.getFlowNode();

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(
            flowNode.getCalledElement(), scope.getVariableScope());
    if (jsonNode != null) {
      VariablesDTO commandVariables;
      if (callActivityInstance.getFlowNode().isPropagateAllParentVariables()) {
        commandVariables = scope.getVariableScope().scopeAndParentsToDto();
      } else {
        commandVariables = scope.getVariableScope().scopeToDTO();
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
      callActivityInstance.setState(ExecutionState.ABORTED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      CallActivityInstance instance,
      ContinueFlowElementTriggerDTO trigger) {
    instance.setState(ExecutionState.COMPLETED);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      CallActivityInstance instance) {
    processInstanceProcessingContext
        .getInstanceResult()
        .addTerminateCommand(new AbortTriggerDTO(instance.getChildProcessInstanceId(), List.of()));
  }
}
