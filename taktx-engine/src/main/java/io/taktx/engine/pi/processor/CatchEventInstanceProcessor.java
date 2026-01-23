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
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.SignalEvent;
import io.taktx.engine.pd.model.SignalEventDefinition;
import io.taktx.engine.pi.InstanceResult;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CancelInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.CatchEventInstance;
import io.taktx.engine.pi.model.NewInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class CatchEventInstanceProcessor<
        E extends CatchEvent, I extends CatchEventInstance<? extends CatchEvent>>
    extends EventInstanceProcessor<E, I> {

  @Getter private FeelExpressionHandler feelExpressionHandler;

  protected CatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      FeelExpressionHandler feelExpressionHandler,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
    this.feelExpressionHandler = feelExpressionHandler;
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I catchEventInstance,
      String inputFlowId) {

    catchEventInstance
        .getFlowNode()
        .getSignalEventDefinition()
        .ifPresent(
            signalEventDefinition -> {
              catchEventInstance.setState(ExecutionState.ACTIVE);

              if (signalEventDefinition.getReferencedSignal() == null) {
                throw new ProcessInstanceException(
                    catchEventInstance, "SignalEventDefinition has no referenced signal");
              }

              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(
                      signalEventDefinition.getReferencedSignal().name(), variableScope);

              if (jsonNode == null || jsonNode.isNull()) {
                throw new ProcessInstanceException(
                    catchEventInstance, "Signal name expression returned null");
              }

              String name = jsonNode.asText();
              NewInstanceSignalSubscriptionInfo subscriptionInfo =
                  new NewInstanceSignalSubscriptionInfo(name, catchEventInstance);

              processInstanceProcessingContext
                  .getInstanceResult()
                  .addNewInstanceSignalSubscription(subscriptionInfo);
            });
    processStartSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, variableScope, catchEventInstance);
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    flowNodeInstance.setState(ExecutionState.COMPLETED);

    processContinueSpecificCatchEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  private void terminateSubscriptions(
      I flowNodeInstance, InstanceResult result, VariableScope variableScope) {
    terminateSignalSubscription(flowNodeInstance, result, variableScope);
  }

  private void terminateSignalSubscription(
      I flowNodeInstance, InstanceResult result, VariableScope variableScope) {
    Optional<SignalEventDefinition> signalEventDefinition =
        flowNodeInstance.getFlowNode().getSignalEventDefinition();
    if (signalEventDefinition.isPresent()) {
      SignalEvent referencedSignal = signalEventDefinition.get().getReferencedSignal();
      if (referencedSignal == null) {
        throw new ProcessInstanceException(
            flowNodeInstance, "SignalEventDefinition has no referenced signal");
      }
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(referencedSignal.name(), variableScope);
      if (jsonNode == null || jsonNode.isNull()) {
        throw new ProcessInstanceException(
            flowNodeInstance, "Signal name expression returned null");
      }
      String name = jsonNode.asText();

      result.addCancelInstanceSignalSubscription(
          new CancelInstanceSignalSubscriptionInfo(name, flowNodeInstance));
    }
  }

  protected abstract void processStartSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance);

  protected abstract void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);

  @Override
  protected void processAbortSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I instance) {
    terminateSubscriptions(
        instance, processInstanceProcessingContext.getInstanceResult(), variableScope);
  }
}
