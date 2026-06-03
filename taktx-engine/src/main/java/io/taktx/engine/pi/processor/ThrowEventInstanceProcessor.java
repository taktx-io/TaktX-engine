/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.CompensationEventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.SignalEvent;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CompensationRegistration;
import io.taktx.engine.pi.model.CompensationTriggerState;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ThrowEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
              VariableValue signalNameValue =
                  feelExpressionHandler.processFeelExpression(
                      referencedSignal.name(), variableScope);
              if (signalNameValue == null
                  || signalNameValue.getKindCase() == VariableValue.KindCase.NULL_VALUE
                  || signalNameValue.getKindCase() == VariableValue.KindCase.KIND_NOT_SET) {
                throw new ProcessInstanceException(
                    flowNodeInstance, "Signal name expression returned null");
              }

              String name = String.valueOf(Variables.toJavaObject(signalNameValue));

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
                      Map.of());
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
                      Map.of());
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
                            VariablesDTO.ofVariableMap(childVariableScope.scopeToMap()));
                    ContinueFlowNodeInstanceInfo continueFlowNodeInstanceInfo =
                        new ContinueFlowNodeInstanceInfo(
                            catchEventInstance, trigger, childVariableScope);
                    scope
                        .getDirectInstanceResult()
                        .addContinueInstance(continueFlowNodeInstanceInfo);
                  });
            });

    Optional<CompensationEventDefinition> compensationDef =
        flowNodeInstance.getFlowNode().getCompensationEventDefinition();
    if (compensationDef.isPresent()) {
      startCompensation(compensationDef.get(), flowNodeInstance, scope, variableScope);
      if (flowNodeInstance.isActive()) {
        // Handlers were started; throw event stays active until they complete
        processStartSpecificThrowEventInstance(
            processInstanceProcessingContext, scope, flowNodeInstance);
        return;
      }
    }

    flowNodeInstance.setState(ExecutionState.COMPLETED);
    processStartSpecificThrowEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  private void startCompensation(
      CompensationEventDefinition ced,
      I flowNodeInstance,
      Scope scope,
      VariableScope variableScope) {
    List<CompensationRegistration> registrations =
        scope.findRegistrationsForThrow(ced.getActivityRef());
    if (registrations.isEmpty()) {
      // Nothing to compensate — complete immediately
      flowNodeInstance.setState(ExecutionState.COMPLETED);
      return;
    }
    flowNodeInstance.setState(ExecutionState.ACTIVE);
    CompensationTriggerState triggerState =
        new CompensationTriggerState(flowNodeInstance.getElementInstanceId(), ced.getActivityRef());
    for (CompensationRegistration reg : registrations) {
      Activity handler = scope.getFlowElements().getActivity(reg.getHandlerId()).orElse(null);
      if (handler == null) {
        continue;
      }
      FlowNodeInstance<?> handlerInstance =
          handler.createAndStoreNewInstance(flowNodeInstance.getParentInstance(), scope);
      VariableScope parentVarScope =
          variableScope.getParentScope() != null ? variableScope.getParentScope() : variableScope;
      VariableScope handlerScope = parentVarScope.selectChildScope(handlerInstance);
      if (reg.getVariableSnapshot() != null) {
        handlerScope.merge(reg.getVariableSnapshot());
      }
      triggerState.addPendingHandler(handlerInstance.getElementInstanceId());
      scope
          .getDirectInstanceResult()
          .addNewFlowNodeInstance(
              new StartFlowNodeInstanceInfo(handlerInstance, null, handlerScope));
      reg.setConsumed(true);
      reg.setConsumedByThrowInstanceKey(flowNodeInstance.getElementInstanceId());
    }
    if (triggerState.isAllHandlersDone()) {
      // All handlers were no-ops; complete immediately
      flowNodeInstance.setState(ExecutionState.COMPLETED);
    } else {
      scope.addCompensationTriggerState(triggerState);
    }
  }

  @Override
  protected void processContinueSpecificFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      I flowNodeInstance,
      ContinueFlowElementTriggerDTO trigger) {
    // Compensation throw events are continued when all handlers complete
    flowNodeInstance.setState(ExecutionState.COMPLETED);
    scope.removeCompensationTriggerState(
        scope.findTriggerStateByThrowKey(flowNodeInstance.getElementInstanceId()).orElse(null));
  }

  protected abstract void processStartSpecificThrowEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);
}
