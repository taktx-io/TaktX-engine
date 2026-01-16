/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.AbortTriggerDTO;
import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.EventSignalDTO;
import io.taktx.dto.EventSignalTriggerDTO;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.VariablesDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventBasedGateway;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pi.model.ContinueFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithScope;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class ScopeProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FeelExpressionHandler feelExpressionHandler;
  private final DtoMapper dtoMapper;

  public ScopeProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FeelExpressionHandler feelExpressionHandler,
      DtoMapper dtoMapper) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.feelExpressionHandler = feelExpressionHandler;
    this.dtoMapper = dtoMapper;
  }

  public Void processStart(
      List<Long> parentElementInstanceIdPath,
      String elementId,
      VariablesDTO variables,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < parentElementInstanceIdPath.size()) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(parentElementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId == null) {
        // Element should be there. We can't start an element in a subprocess which is not there
        throw new IllegalArgumentException(
            "No element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " found on level "
                + subProcessLevel);
      }

      if (instanceWithInstanceId instanceof WithScope withScope) {

        VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);

        processStart(
            parentElementInstanceIdPath,
            elementId,
            variables,
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope);

        bubbleUpEvents(scope, withScope);

        ContinueFlowElementTriggerDTO trigger =
            new ContinueFlowElementTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                parentElementInstanceIdPath,
                null,
                VariablesDTO.empty());
        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, trigger, childVariableScope));
        doBusiness(processInstanceProcessingContext, scope, variableScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == parentElementInstanceIdPath.size()) {
      if (scope.getActiveCnt() == 0) {
        scope
            .getSubscriptions()
            .startSubscriptionsForEventSubprocesses(
                processInstanceProcessingContext, scope, feelExpressionHandler, variableScope);
      }

      createNewInstanceAndAddToDirectInstanceResult(scope, elementId, variableScope, variables);

      doBusiness(processInstanceProcessingContext, scope, variableScope);
    } else {
      throw new IllegalArgumentException(
          "Subprocess level "
              + subProcessLevel
              + " is out of bounds for parentElementInstanceIdPath "
              + parentElementInstanceIdPath);
    }
    return null;
  }

  public Void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      ContinueFlowElementTriggerDTO trigger,
      List<Long> elementInstanceIdPath) {

    int subProcessLevel = scope.getSubProcessLevel();
    if (subProcessLevel < elementInstanceIdPath.size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(elementInstanceIdPath.get(subProcessLevel));
      VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);
      if (instanceWithInstanceId instanceof WithScope withScope) {
        processContinue(
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope,
            trigger,
            elementInstanceIdPath);
        bubbleUpEvents(scope, withScope);

        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, trigger, childVariableScope));

        doBusiness(processInstanceProcessingContext, scope, variableScope);
      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + elementInstanceIdPath.get(subProcessLevel)
                + " is not a scope but "
                + instanceWithInstanceId.getClass().getName());
      }
    } else {
      FlowNodeInstance<?> flowNodeInstance =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(
                  trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel()));
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);

      if (trigger instanceof EventSignalTriggerDTO eventSignalTriggerDTO) {
        for (EventSignalDTO eventSignalDTO : eventSignalTriggerDTO.getEventSignalList()) {
          EventSignal eventSignal = dtoMapper.map(eventSignalDTO);
          eventSignal.getPathToSource().addFirst(flowNodeInstance);
          scope.getDirectInstanceResult().addEvent(eventSignal);
        }
      } else {
        ContinueFlowNodeInstanceInfo continueInstance =
            new ContinueFlowNodeInstanceInfo(flowNodeInstance, trigger, childVariableScope);

        scope.getDirectInstanceResult().addContinueInstance(continueInstance);
      }
      doBusiness(processInstanceProcessingContext, scope, variableScope);
    }
    return null;
  }

  public Void processAbort(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      AbortTriggerDTO trigger) {
    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < trigger.getElementInstanceIdPath().size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(trigger.getElementInstanceIdPath().get(subProcessLevel));
      VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);

      if (instanceWithInstanceId instanceof WithScope withScope) {
        processAbort(
            processInstanceProcessingContext, withScope.getScope(), childVariableScope, trigger);
        bubbleUpEvents(scope, withScope);

        ContinueFlowElementTriggerDTO continueTrigger =
            new ContinueFlowElementTriggerDTO(
                processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
                trigger.getElementInstanceIdPath(),
                null,
                VariablesDTO.empty());
        scope
            .getDirectInstanceResult()
            .addContinueInstance(
                new ContinueFlowNodeInstanceInfo(
                    instanceWithInstanceId, continueTrigger, childVariableScope));

        doBusiness(processInstanceProcessingContext, scope, variableScope);
      }
    } else {
      if (!trigger.getElementInstanceIdPath().isEmpty()) {
        FlowNodeInstance<?> flowNodeInstance =
            scope
                .getFlowNodeInstances()
                .getInstanceWithInstanceId(
                    trigger.getElementInstanceIdPath().get(scope.getSubProcessLevel()));
        scope.getDirectInstanceResult().addAbortInstance(flowNodeInstance);
      } else {
        Map<Long, FlowNodeInstance<?>> allInstances =
            scope.getFlowNodeInstances().getAllInstances();
        allInstances
            .values()
            .forEach(
                flowNodeInstance ->
                    scope.getDirectInstanceResult().addAbortInstance(flowNodeInstance));
        scope.setState(ExecutionState.ABORTED);
      }
      doBusiness(processInstanceProcessingContext, scope, variableScope);
    }
    return null;
  }

  public void doBusiness(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();
    while (directInstanceResult.hasDirectTriggers()) {
      if (directInstanceResult.isAbortScope()) {
        log.info("Aborting scope {}", scope.getParentFlowNodeInstance());
        abortScope(scope);
        directInstanceResult.resetAbortScope();
      }
      processEvents(processInstanceProcessingContext, scope, variableScope);
      processAbortInstances(processInstanceProcessingContext, scope, variableScope);
      processNewInstances(processInstanceProcessingContext, scope);
      processContinueInstances(processInstanceProcessingContext, scope);
    }
    scope.updateActiveCountForInstances();

    scope
        .getSubscriptions()
        .terminateEventSubprocessSubscriptionsIfDone(processInstanceProcessingContext, scope);
  }

  private void abortScope(Scope scope) {
    scope
        .getFlowNodeInstances()
        .getAllInstances()
        .values()
        .forEach(
            instance -> {
              if (instance.isActive()) {
                scope.getDirectInstanceResult().addAbortInstance(instance);
              }
            });
    scope.setState(ExecutionState.ABORTED);
  }

  public void bubbleUpEvents(Scope scope, WithScope withScope) {
    EventSignal bubbleUpEventSignal =
        withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    while (bubbleUpEventSignal != null) {
      bubbleUpEventSignal.bubbleUp();
      scope.getDirectInstanceResult().addEvent(bubbleUpEventSignal);
      bubbleUpEventSignal = withScope.getScope().getDirectInstanceResult().pollBubbleUpEvent();
    }
  }

  private void processEvents(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    EventSignal eventSignal = scope.getDirectInstanceResult().pollEvent();
    while (eventSignal != null) {
      processEventByFlowNodeInstance(
          processInstanceProcessingContext,
          scope,
          variableScope,
          eventSignal,
          eventSignal.getCurrentInstance());
      eventSignal = scope.getDirectInstanceResult().pollEvent();
    }
  }

  private void processNewInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    StartFlowNodeInstanceInfo instanceInfo =
        scope.getDirectInstanceResult().pollNewFlowNodeInstance();
    while (instanceInfo != null) {
      FlowNodeInstance<?> flowNodeInstance = instanceInfo.flowNodeInstance();
      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              flowNodeInstance.getFlowNode(), flowNodeInstance.isIteration());
      processor.processStart(
          processInstanceProcessingContext,
          scope,
          instanceInfo.variableScope(),
          flowNodeInstance,
          instanceInfo.inputSequenceFlowId());

      instanceInfo = scope.getDirectInstanceResult().pollNewFlowNodeInstance();
    }
  }

  private void processContinueInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext, Scope scope) {

    ContinueFlowNodeInstanceInfo continueInstance =
        scope.getDirectInstanceResult().pollContinueInstance();
    while (continueInstance != null) {
      FlowNodeInstance<?> flowNodeInstance = continueInstance.flowNodeInstance();
      VariableScope variableScope = continueInstance.variableScope();

      if (flowNodeInstance instanceof IntermediateCatchEventInstance intermediateCatchEventInstance
          && intermediateCatchEventInstance.isActive()) {
        Optional<EventBasedGateway> eventBasedGateway =
            flowNodeInstance.getFlowNode().getIncomingSequenceFlows().stream()
                .filter(flow -> flow.getSourceNode() instanceof EventBasedGateway)
                .map(flow -> (EventBasedGateway) flow.getSourceNode())
                .findFirst();
        if (eventBasedGateway.isPresent()) {
          Long gatewayInstanceId = scope.getGatewayInstanceId(eventBasedGateway.get().getId());
          if (gatewayInstanceId != null) {
            EventBasedGatewayInstance gatewayInstance =
                (EventBasedGatewayInstance)
                    scope.getFlowNodeInstances().getInstanceWithInstanceId(gatewayInstanceId);
            if (gatewayInstance != null) {
              for (Long connectedInstanceId : gatewayInstance.getConnectedFlowNodeInstanceIds()) {
                if (connectedInstanceId != flowNodeInstance.getElementInstanceId()) {
                  FlowNodeInstance<?> connectedInstance =
                      scope.getFlowNodeInstances().getInstanceWithInstanceId(connectedInstanceId);
                  if (connectedInstance != null && connectedInstance.isActive()) {
                    scope.getDirectInstanceResult().addAbortInstance(connectedInstance);
                  }
                }
              }
            }
          }
        }
      }

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              flowNodeInstance.getFlowNode(), flowNodeInstance.isIteration());

      processor.processContinue(
          processInstanceProcessingContext,
          scope,
          continueInstance.variableScope(),
          flowNodeInstance,
          continueInstance.trigger());

      continueInstance = scope.getDirectInstanceResult().pollContinueInstance();
    }
  }

  private void processAbortInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    FlowNodeInstance<?> abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    while (abortInstance != null) {

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              abortInstance.getFlowNode(), abortInstance.isIteration());

      VariableScope childVariableScope = variableScope.selectChildScope(abortInstance);
      processor.processAbort(
          processInstanceProcessingContext, scope, childVariableScope, abortInstance);

      abortInstance = scope.getDirectInstanceResult().pollAbortInstance();
    }
  }

  private static StartFlowNodeInstanceInfo createNewInstanceAndAddToDirectInstanceResult(
      Scope scope, String elementId, VariableScope parentVariableScope, VariablesDTO variables) {
    FlowNode flowNode = scope.getFlowElements().getStartNode(elementId);

    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(scope.getParentFlowNodeInstance(), scope);

    VariableScope childVariableScope = parentVariableScope.selectChildScope(flowNodeInstance);
    childVariableScope.merge(variables);
    DirectInstanceResult directInstanceResult = scope.getDirectInstanceResult();

    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
        new StartFlowNodeInstanceInfo(flowNodeInstance, null, childVariableScope);

    directInstanceResult.addNewFlowNodeInstance(startFlowNodeInstanceInfo);
    return startFlowNodeInstanceInfo;
  }

  private void processEventByFlowNodeInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope,
      EventSignal event,
      FlowNodeInstance<?> fLowNodeInstance) {

    boolean eventHandled =
        scope
            .getSubscriptions()
            .processEvent(scope, variableScope, event, event.getCurrentInstance());

    if (!eventHandled) {
      scope.getDirectInstanceResult().addBubbleUpEvent(event);
    }
  }

  public Void processSetVariables(
      List<Long> parentElementInstanceIdPath,
      VariablesDTO variables,
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      VariableScope variableScope) {

    int subProcessLevel = scope.getSubProcessLevel();

    if (subProcessLevel < parentElementInstanceIdPath.size() - 1) {
      FlowNodeInstance<?> instanceWithInstanceId =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(parentElementInstanceIdPath.get(subProcessLevel));
      if (instanceWithInstanceId == null) {
        // Element should be there. We can't start an element in a subprocess which is not there
        throw new IllegalArgumentException(
            "No element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " found on level "
                + subProcessLevel);
      }

      if (instanceWithInstanceId instanceof WithScope withScope) {
        VariableScope childVariableScope = variableScope.selectChildScope(instanceWithInstanceId);
        processSetVariables(
            parentElementInstanceIdPath,
            variables,
            processInstanceProcessingContext,
            withScope.getScope(),
            childVariableScope);

      } else {
        throw new IllegalArgumentException(
            "Element with instance id "
                + parentElementInstanceIdPath.get(subProcessLevel)
                + " is not a scope");
      }
    } else if (subProcessLevel == (parentElementInstanceIdPath.size() - 1)
        && !parentElementInstanceIdPath.isEmpty()) {
      FlowNodeInstance<?> flowNodeInstance =
          scope
              .getFlowNodeInstances()
              .getInstanceWithInstanceId(
                  parentElementInstanceIdPath.get(scope.getSubProcessLevel()));
      VariableScope childVariableScope = variableScope.selectChildScope(flowNodeInstance);

      FlowNodeInstanceProcessor<?, ?, ?> processor =
          flowNodeInstanceProcessorProvider.getProcessor(
              flowNodeInstance.getFlowNode(), flowNodeInstance.isIteration());

      processor.processSetVariables(
          processInstanceProcessingContext, scope, flowNodeInstance, childVariableScope, variables);

    } else if (parentElementInstanceIdPath.isEmpty()) {
      variableScope.merge(variables);
    } else {
      throw new IllegalArgumentException(
          "Subprocess level "
              + subProcessLevel
              + " is out of bounds for parentElementInstanceIdPath "
              + parentElementInstanceIdPath);
    }
    return null;
  }
}
