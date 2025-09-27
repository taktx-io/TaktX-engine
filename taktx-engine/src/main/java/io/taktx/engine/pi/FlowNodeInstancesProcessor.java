/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.FlowNodeStateEnum;
import io.taktx.dto.ScopeState;
import io.taktx.dto.StartFlowElementTriggerDTO;
import io.taktx.dto.TerminateTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.MessageEventDefinition;
import io.taktx.engine.pd.model.StartEvent;
import io.taktx.engine.pd.model.SubProcess;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pd.model.WIthChildElements;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstances;
import io.taktx.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.ScheduledStartInfo;
import io.taktx.engine.pi.model.SubProcessInstance;
import io.taktx.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessor;
import io.taktx.engine.pi.processor.FlowNodeInstanceProcessorProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FlowNodeInstancesProcessor {

  private final FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider;
  private final FlowInstanceRunner flowInstanceRunner;
  private final PathExtractor pathExtractor;
  private final FeelExpressionHandler feelExpressionHandler;
  private final ProcessInstanceMapper mapper;

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      PathExtractor pathExtractor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper mapper) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.pathExtractor = pathExtractor;
    this.feelExpressionHandler = feelExpressionHandler;
    this.mapper = mapper;
  }

  public void processStart(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      VariableScope parentVariableScope) {

    FlowNodeInstances flowNodeInstances = flowNodeInstanceProcessingContext.getFlowNodeInstances();

    flowNodeInstances.setState(ScopeState.ACTIVE);

    // first check if we need to start timer triggers for event subprocesses with corresponding
    // timer start events
    FlowElements flowElements = flowNodeInstanceProcessingContext.getFlowElements();
    List<SubProcess> eventTriggeredSubProcesses = flowElements.getEventTriggeredSubProcesses();
    for (SubProcess eventTriggeredSubProcess : eventTriggeredSubProcesses) {
      List<StartEvent> startEvents = eventTriggeredSubProcess.getElements().getStartEvents();
      for (StartEvent startEvent : startEvents) {
        Optional<TimerEventDefinition> optTimerEventDefinition =
            startEvent.getTimerEventDefinition();
        if (optTimerEventDefinition.isPresent()) {
          TimerEventDefinition timerEventDefinition = optTimerEventDefinition.get();
          ScheduledStartInfo scheduledStartInfo =
              new ScheduledStartInfo(
                  flowNodeInstances, eventTriggeredSubProcess, timerEventDefinition);
          processInstanceProcessingContext
              .getInstanceResult()
              .addScheduledStart(scheduledStartInfo);
        }

        Optional<MessageEventDefinition> optMessageventDefinition =
            startEvent.getMessageventDefinition();
        if (optMessageventDefinition.isPresent()) {
          MessageEventDefinition messageEventDefinition = optMessageventDefinition.get();

          String correlationKey =
              feelExpressionHandler
                  .processFeelExpression(
                      messageEventDefinition.getReferencedMessage().correlationKey(),
                      parentVariableScope)
                  .asText();

          String messageName = messageEventDefinition.getReferencedMessage().name();
          NewCorrelationSubscriptionMessageEventInfo messageSubscription =
              new NewCorrelationSubscriptionMessageEventInfo(
                  messageName,
                  correlationKey,
                  flowNodeInstances.getParentFlowNodeInstance(),
                  eventTriggeredSubProcess);

          flowNodeInstances.addMessageSubscription(messageName, correlationKey);

          processInstanceProcessingContext
              .getInstanceResult()
              .addNewCorrelationSubcriptionMessageEvent(messageSubscription);
        }
      }
    }

    FlowNode flowNode = flowElements.getStartNode(elementId);

    FlowNodeInstance<?> flowNodeInstance =
        flowNode.createAndStoreNewInstance(parentElementInstance, flowNodeInstances);

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNode);

    processor.processStart(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        null,
        parentVariableScope);

    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);

    // Check if we happen to be in an event subprocess that is triggered by an event and terminate
    // all awaiting instances
    // if the start event is interrupting
    if (parentElementInstance instanceof SubProcessInstance subProcessInstance
        && subProcessInstance.getFlowNode().isTriggeredByEvent()
        && flowNode instanceof StartEvent startEvent
        && startEvent.isInterrupting()) {
      List<Long> instancePath = pathExtractor.getInstancePath(subProcessInstance);
      flowNodeInstanceProcessingContext
          .getDirectInstanceResult()
          .setTerminateParentPath(instancePath);
    }
  }

  public void processStartFlowElement(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      StartFlowElementTriggerDTO trigger,
      VariableScope parentVariableScope) {

    if (flowNodeInstanceProcessingContext.getSubProcessLevel()
        < trigger.getParentElementInstanceIdPath().size()) {
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
              flowNodeInstanceProcessingContext.getFlowNodeInstances(),
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              flowNodeInstanceProcessingContext.getFlowElements(),
              mapper);

      FlowNodeInstance<?> flowNodeInstance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger
                  .getParentElementInstanceIdPath()
                  .get(flowNodeInstanceProcessingContext.getSubProcessLevel()));

      if (flowNodeInstance instanceof SubProcessInstance subProcessInstance
          && flowNodeInstance.getFlowNode() instanceof WIthChildElements wIthChildElements) {
        FlowNodeInstanceProcessingContext childFlowNodeInstanceProcessingContext =
            new FlowNodeInstanceProcessingContext(
                subProcessInstance.getFlowNodeInstances(),
                flowNodeInstanceProcessingContext.getSubProcessLevel() + 1,
                wIthChildElements.getElements());
        processStartFlowElement(
            processInstanceProcessingContext,
            childFlowNodeInstanceProcessingContext,
            trigger,
            parentVariableScope);

        continueNewInstances(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            parentVariableScope);

        if (subProcessInstance.getFlowNodeInstances().getState().isDone()) {
          subProcessInstance.setState(FlowNodeStateEnum.COMPLETED);
        }

        flowNodeInstanceProcessingContext.getFlowNodeInstances().determineImplicitCompletedState();
      } else {
        throw new IllegalStateException(
            "Parent element instanceToContinue is not a WithFlowNodeInstances or WIthChildElements type: "
                + flowNodeInstance.getClass().getName());
      }

    } else {

      FlowNodeInstances flowNodeInstances =
          flowNodeInstanceProcessingContext.getFlowNodeInstances();
      FlowElements parentFlowElements = flowNodeInstanceProcessingContext.getFlowElements();
      FlowNodeInstance<?> parentFlowNodeInstance = flowNodeInstances.getParentFlowNodeInstance();
      Optional<FlowNode> optFlowNode = parentFlowElements.getFlowNode(trigger.getElementId());
      if (optFlowNode.isPresent()) {
        FlowNode flowNode = optFlowNode.get();
        FlowNodeInstance<?> flowNodeInstance =
            flowNode.createAndStoreNewInstance(parentFlowNodeInstance, flowNodeInstances);

        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(flowNode);

        processor.processStart(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            flowNodeInstance,
            null,
            parentVariableScope);

        continueNewInstances(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            parentVariableScope);
      }
    }
  }

  public void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope parentVariables) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            flowNodeInstanceProcessingContext.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            mapper);

    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger
                .getElementInstanceIdPath()
                .get(flowNodeInstanceProcessingContext.getSubProcessLevel()));

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        trigger,
        parentVariables);

    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariables);
  }

  public void processTerminate(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      TerminateTriggerDTO trigger,
      VariableScope parentVariableScope) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceId(),
            flowNodeInstanceProcessingContext.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements(),
            mapper);

    if (trigger.getElementInstanceIdPath().isEmpty()) {
      // Terminate all elements in the process instanceToContinue and the process instanceToContinue
      // itself
      storedFlowNodeInstancesWrapper
          .getAllInstances()
          .values()
          .forEach(
              instance -> {
                FlowNodeInstanceProcessor<?, ?, ?> processor =
                    flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
                processor.processAbort(
                    processInstanceProcessingContext,
                    flowNodeInstanceProcessingContext,
                    instance,
                    parentVariableScope);
              });
      flowNodeInstanceProcessingContext.getFlowNodeInstances().setState(ScopeState.CANCELED);
    } else {
      // Terminate the specific element instanceToContinue in the process instanceToContinue
      FlowNodeInstance<?> instance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processAbort(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            instance,
            parentVariableScope);
      }
    }
    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);
  }

  private void continueNewInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      VariableScope parentVariableScope) {

    flowInstanceRunner.continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);

    DirectInstanceResult directInstanceResult =
        flowNodeInstanceProcessingContext.getDirectInstanceResult();

    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while (eventSignal != null) {
      processInstanceProcessingContext.getInstanceResult().addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }
    flowNodeInstanceProcessingContext.getFlowNodeInstances().determineImplicitCompletedState();

    terminateEventSubprocessSubscriptions(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext);
  }

  private static void terminateEventSubprocessSubscriptions(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext) {
    if (flowNodeInstanceProcessingContext.getFlowNodeInstances().getState().isDone()) {
      flowNodeInstanceProcessingContext
          .getFlowNodeInstances()
          .getMessageSubscriptions()
          .forEach(
              (messageName, correlationKeys) ->
                  correlationKeys.forEach(
                      correlationKey -> {
                        TerminateCorrelationSubscriptionMessageEventInfo messageEventInfo =
                            new TerminateCorrelationSubscriptionMessageEventInfo(
                                messageName, correlationKey);
                        processInstanceProcessingContext
                            .getInstanceResult()
                            .addTerminateCorrelationSubscriptionMessageEvent(messageEventInfo);
                      }));

      flowNodeInstanceProcessingContext
          .getFlowNodeInstances()
          .getScheduleKeys()
          .forEach(
              scheduleKey ->
                  processInstanceProcessingContext.getInstanceResult().cancelSchedule(scheduleKey));
    }
  }
}
