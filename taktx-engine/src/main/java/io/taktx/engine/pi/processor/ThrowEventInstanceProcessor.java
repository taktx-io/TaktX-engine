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
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.SignalEvent;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ThrowEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  private FeelExpressionHandler feelExpressionHandler;

  protected ThrowEventInstanceProcessor(
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
      I flowNodeInstance,
      String inputFlowId) {

    flowNodeInstance
        .getFlowNode()
        .getTerminateEventDefinition()
        .ifPresent(
            terminateEventDefinition -> {
              log.info("Terminate event encountered, aborting process instance");
              scope.getDirectInstanceResult().setAbortScope();
            });

    flowNodeInstance
        .getFlowNode()
        .getSignalEventDefinition()
        .ifPresent(
            signalEventDefinition -> {
              SignalEvent referencedSignal = signalEventDefinition.getReferencedSignal();
              if (referencedSignal == null) {
                throw new ProcessInstanceException(
                    flowNodeInstance, "SignalEventDefinition has no referenced signal");
              }
              JsonNode jsonNode =
                  feelExpressionHandler.processFeelExpression(
                      referencedSignal.name(), variableScope);
              if (jsonNode == null || jsonNode.isNull()) {
                throw new ProcessInstanceException(
                    flowNodeInstance, "Signal name expression returned null");
              }

              String name = jsonNode.asText();

              processInstanceProcessingContext.getInstanceResult().addSignal(name);
            });

    flowNodeInstance
        .getFlowNode()
        .getErrorEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              EventSignal errorEvent =
                  new ErrorEventSignal(
                      flowNodeInstance,
                      errorEventDefinition.getReferencedError().code(),
                      "",
                      VariablesDTO.empty());
              scope.getDirectInstanceResult().addEvent(errorEvent);
            });

    flowNodeInstance
        .getFlowNode()
        .getEscalationEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              EventSignal escalationEventSignal =
                  new EscalationEventSignal(
                      flowNodeInstance,
                      errorEventDefinition.getReferencedEscalation().code(),
                      "",
                      VariablesDTO.empty());
              scope.getDirectInstanceResult().addEvent(escalationEventSignal);
            });

    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinition()
        .ifPresent(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent> intermediateCatchEvent =
                  scope
                      .getFlowElements()
                      .getIntermediateCatchEventWithName(linkEventDefinition.getName());
              intermediateCatchEvent.ifPresent(
                  event -> {
                    FlowNodeInstance<?> catchEventInstance =
                        event.createAndStoreNewInstance(
                            flowNodeInstance.getParentInstance(), scope);
                    VariableScope childVariableScope =
                        variableScope.getParentScope().selectChildScope(catchEventInstance);
                    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                        new StartFlowNodeInstanceInfo(catchEventInstance, null, childVariableScope);
                    scope
                        .getDirectInstanceResult()
                        .addNewFlowNodeInstance(startFlowNodeInstanceInfo);
                    ContinueFlowElementTriggerDTO trigger =
                        new ContinueFlowElementTriggerDTO(
                            scope.getProcessInstanceId(),
                            catchEventInstance.createKeyPath(),
                            null,
                            childVariableScope.scopeToDTO());
                    ContinueFlowNodeInstanceInfo continueFlowNodeInstanceInfo =
                        new ContinueFlowNodeInstanceInfo(
                            catchEventInstance, trigger, childVariableScope);
                    scope
                        .getDirectInstanceResult()
                        .addContinueInstance(continueFlowNodeInstanceInfo);
                  });
            });

    flowNodeInstance.setState(ExecutionState.COMPLETED);
    processStartSpecificThrowEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);
}
