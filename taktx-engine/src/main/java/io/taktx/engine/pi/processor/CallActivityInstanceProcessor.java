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

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.v_1_0_0.ActtivityStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.dto.v_1_0_0.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.CallActivity;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.NewStartCommand;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
import io.taktx.engine.pi.model.CallActivityInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      CallActivityInstance callActivityInstance,
      String inputFlowId,
      VariableScope variables) {
    callActivityInstance.setState(ActtivityStateEnum.WAITING);

    UUID newProcessInstanceKey = UUID.randomUUID();
    callActivityInstance.setChildProcessInstanceId(newProcessInstanceKey);
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

      processingContext
          .getInstanceResult()
          .addNewStartCommand(
              new NewStartCommand(
                  newProcessInstanceKey,
                  flowNode,
                  callActivityInstance,
                  jsonNode.asText(),
                  commandVariables,
                  callActivityInstance.getFlowNode().isPropagateAllChildVariables(),
                  callActivityInstance.getFlowNode().getIoMapping().getOutputMappings()));
    } else {
      callActivityInstance.setState(ActtivityStateEnum.FAILED);
    }
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      CallActivityInstance instance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables) {
    instance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      CallActivityInstance instance,
      VariableScope processInstanceVariables) {
    processingContext.getInstanceResult().addTerminateCommand(instance.getChildProcessInstanceId());
  }
}
