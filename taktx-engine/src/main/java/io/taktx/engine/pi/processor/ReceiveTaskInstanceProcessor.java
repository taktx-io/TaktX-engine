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
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.ReceiveTask;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
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
    processingContext
        .getInstanceResult()
        .addNewCorrelationSubcriptionMessageEvent(
            new NewCorrelationSubscriptionMessageEventInfo(
                messageName, correlationKey, receiveTaskInstance));
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      ReceiveTaskInstance receiveTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables) {
    receiveTaskInstance.setState(ActtivityStateEnum.FINISHED);
    terminatingSubscriptionInstanceResult(
        processingContext.getInstanceResult(), receiveTaskInstance);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      ReceiveTaskInstance instance,
      VariableScope processInstanceVariables) {
    terminatingSubscriptionInstanceResult(processingContext.getInstanceResult(), instance);
  }

  private static void terminatingSubscriptionInstanceResult(
      InstanceResult instanceResult, ReceiveTaskInstance receiveTaskInstance) {
    String messageName = receiveTaskInstance.getFlowNode().getReferencedMessage().name();
    instanceResult.addTerminateCorrelationSubscriptionMessageEvent(
        new TerminateCorrelationSubscriptionMessageEventInfo(
            messageName, receiveTaskInstance.getCorrelationKey()));
  }
}
