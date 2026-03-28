/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.Scope;
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
      Scope scope,
      VariableScope variableScope,
      CallActivityInstance callActivityInstance,
      String inputFlowId) {
    callActivityInstance.setState(ExecutionState.ACTIVE);

    UUID newProcessInstanceId = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceId);
    CallActivity flowNode = callActivityInstance.getFlowNode();

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(flowNode.getCalledElement(), variableScope);
    if (jsonNode == null || jsonNode.isNull()) {
      throw new ProcessInstanceException(
          callActivityInstance, "Called element expression returned null");
    }

    VariablesDTO commandVariables;
    if (callActivityInstance.getFlowNode().isPropagateAllParentVariables()) {
      commandVariables = variableScope.scopeAndParentsToDto();
    } else {
      commandVariables = variableScope.scopeToDTO();
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
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      CallActivityInstance instance,
      ContinueFlowElementTriggerDTO trigger) {
    instance.setState(ExecutionState.COMPLETED);
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      CallActivityInstance instance) {
    processInstanceProcessingContext
        .getInstanceResult()
        .addTerminateCommand(new AbortTriggerDTO(instance.getChildProcessInstanceId(), List.of()));
  }
}
