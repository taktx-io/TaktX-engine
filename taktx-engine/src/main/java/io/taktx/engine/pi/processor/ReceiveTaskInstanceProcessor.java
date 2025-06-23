/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.ActtivityStateEnum;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class ReceiveTaskInstanceProcessor
    extends ActivityInstanceProcessor<
        ReceiveTask, ReceiveTaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public ReceiveTaskInstanceProcessor(
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
      ReceiveTaskInstance receiveTaskInstance,
      String inputFlowId,
      VariableScope variables) {
    receiveTaskInstance.setState(ActtivityStateEnum.WAITING);

    ReceiveTask receiveTask = receiveTaskInstance.getFlowNode();
    String correlationKeyExpression = receiveTask.getReferencedMessage().correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(correlationKeyExpression, variables);
    String correlationKey = jsonNode.asText();
    String messageName = receiveTask.getReferencedMessage().name();
    receiveTaskInstance.setCorrelationKey(correlationKey);
    processInstanceProcessingContext
        .getInstanceResult()
        .addNewCorrelationSubcriptionMessageEvent(
            new NewCorrelationSubscriptionMessageEventInfo(
                messageName, correlationKey, receiveTaskInstance, null));
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables) {
    receiveTaskInstance.setState(ActtivityStateEnum.FINISHED);
    terminatingSubscriptionInstanceResult(
        processInstanceProcessingContext.getInstanceResult(), receiveTaskInstance);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ReceiveTaskInstance instance,
      VariableScope processInstanceVariables) {
    terminatingSubscriptionInstanceResult(
        processInstanceProcessingContext.getInstanceResult(), instance);
  }

  private static void terminatingSubscriptionInstanceResult(
      InstanceResult instanceResult, ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTaskInstance.getFlowNode().getReferencedMessage().name();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
  }
}
