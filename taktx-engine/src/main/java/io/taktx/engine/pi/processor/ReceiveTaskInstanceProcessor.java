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
import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Message;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
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
      Scope scope,
      ReceiveTaskInstance receiveTaskInstance,
      String inputFlowId) {
    receiveTaskInstance.setState(ExecutionState.ACTIVE);

    ReceiveTask receiveTask = receiveTaskInstance.getFlowNode();
    Message referencedMessage = receiveTask.getReferencedMessage();
    if (referencedMessage == null) {
      throw new ProcessInstanceException(
          receiveTaskInstance, "ReceiveTask does not reference a message");
    }
    String correlationKeyExpression = referencedMessage.correlationKey();
    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(
            correlationKeyExpression, scope.getVariableScope());
    String correlationKey = jsonNode != null ? jsonNode.asText() : null;
    if (correlationKey == null) {
      throw new ProcessInstanceException(
          receiveTaskInstance, "Correlation key expression returned null");
    }

    String messageName = referencedMessage.name();
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
      Scope scope,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTriggerDTO trigger) {
    receiveTaskInstance.setState(ExecutionState.COMPLETED);
    terminatingSubscriptionInstanceResult(
        processInstanceProcessingContext.getInstanceResult(), receiveTaskInstance);
  }

  @Override
  protected void processAbortSpecificActivityInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      ReceiveTaskInstance instance) {
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
