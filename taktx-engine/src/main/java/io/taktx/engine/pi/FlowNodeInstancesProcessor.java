/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi;

import io.taktx.dto.ContinueFlowElementTriggerDTO;
import io.taktx.dto.ProcessInstanceState;
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
import io.taktx.engine.pi.model.VariableScope;
import io.taktx.engine.pi.model.WithFlowNodeInstances;
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

  public FlowNodeInstancesProcessor(
      FlowNodeInstanceProcessorProvider flowNodeInstanceProcessorProvider,
      FlowInstanceRunner flowInstanceRunner,
      PathExtractor pathExtractor,
      FeelExpressionHandler feelExpressionHandler) {
    this.flowNodeInstanceProcessorProvider = flowNodeInstanceProcessorProvider;
    this.flowInstanceRunner = flowInstanceRunner;
    this.pathExtractor = pathExtractor;
    this.feelExpressionHandler = feelExpressionHandler;
  }

  public void processStart(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      String elementId,
      FlowNodeInstance<?> parentElementInstance,
      VariableScope parentVariableScope) {

    FlowNodeInstances flowNodeInstances = flowNodeInstanceProcessingContext.getFlowNodeInstances();

    flowNodeInstances.setState(ProcessInstanceState.ACTIVE);

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
                  flowNodeInstances.getParentFlowNodeInstance(),
                  eventTriggeredSubProcess,
                  timerEventDefinition);
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

          NewCorrelationSubscriptionMessageEventInfo messageSubscription =
              new NewCorrelationSubscriptionMessageEventInfo(
                  messageEventDefinition.getReferencedMessage().name(),
                  correlationKey,
                  flowNodeInstances.getParentFlowNodeInstance(),
                  eventTriggeredSubProcess);

          processInstanceProcessingContext
              .getInstanceResult()
              .addNewCorrelationSubcriptionMessageEvent(messageSubscription);
        }
      }
    }

    FlowNode flowNode = flowElements.getStartNode(elementId);

    // Check if we happen to be in an event subprocess that is triggered by an event and terminate
    // all awaiting instances
    // if the start event is interrupting
    if (parentElementInstance instanceof SubProcessInstance subProcessInstance
        && subProcessInstance.getFlowNode().isTriggeredByEvent()
        && flowNode instanceof StartEvent startEvent
        && startEvent.isInterrupting()) {
      flowNodeInstanceProcessingContext.getDirectInstanceResult().setTerminateParent();
    }

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

    flowNodeInstances.determineImplicitCompletedState();
  }

  public void processStartFlowElement(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      StartFlowElementTriggerDTO trigger,
      int subProcessLevel,
      VariableScope parentVariableScope) {

    FlowNodeInstance<?> parentFlowNodeInstance = null;
    FlowNodeInstances parentFlowNodeInstances =
        flowNodeInstanceProcessingContext.getFlowNodeInstances();
    FlowElements parentFlowElements = flowNodeInstanceProcessingContext.getFlowElements();
    while (subProcessLevel < trigger.getParentElementInstanceIdPath().size()) {
      // drill down into the flownode instances to find the final parent element instanceToContinue
      StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
          new StoredFlowNodeInstancesWrapper(
              processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
              parentFlowNodeInstances,
              processInstanceProcessingContext.getFlowNodeInstanceStore(),
              parentFlowElements);
      parentFlowNodeInstance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger.getParentElementInstanceIdPath().get(subProcessLevel++));
      if (parentFlowNodeInstance instanceof WithFlowNodeInstances withFlowNodeInstances
          && parentFlowNodeInstance.getFlowNode() instanceof WIthChildElements wIthChildElements) {
        parentFlowNodeInstances = withFlowNodeInstances.getFlowNodeInstances();
        parentFlowElements = wIthChildElements.getElements();
      } else {
        throw new IllegalStateException(
            "Parent element instanceToContinue is not a WithFlowNodeInstances or WIthChildElements type: "
                + parentFlowNodeInstance.getClass().getName());
      }
    }

    FlowNodeInstances flowNodeInstances = parentFlowNodeInstances;
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
          processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);

      flowNodeInstances.determineImplicitCompletedState();
    }
  }

  public void processContinue(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      int subProcessLevel,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope parentVariables) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstanceProcessingContext.getFlowNodeInstances(),
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowNodeInstanceProcessingContext.getFlowElements());

    FlowNodeInstance<?> flowNodeInstance =
        storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
            trigger.getElementInstanceIdPath().get(subProcessLevel));

    FlowNodeInstanceProcessor<?, ?, ?> processor =
        flowNodeInstanceProcessorProvider.getProcessor(flowNodeInstance.getFlowNode());

    processor.processContinue(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        subProcessLevel,
        flowNodeInstance,
        trigger,
        parentVariables);

    continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariables);

    flowNodeInstanceProcessingContext.getFlowNodeInstances().determineImplicitCompletedState();
  }

  public void processTerminate(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      TerminateTriggerDTO trigger,
      FlowNodeInstances flowNodeInstances,
      VariableScope parentVariableScope,
      FlowElements flowElements) {

    StoredFlowNodeInstancesWrapper storedFlowNodeInstancesWrapper =
        new StoredFlowNodeInstancesWrapper(
            processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
            flowNodeInstances,
            processInstanceProcessingContext.getFlowNodeInstanceStore(),
            flowElements);

    FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext =
        new FlowNodeInstanceProcessingContext(flowNodeInstances, flowElements);
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
                processor.processTerminate(
                    processInstanceProcessingContext,
                    flowNodeInstanceProcessingContext,
                    instance,
                    parentVariableScope);
              });
      flowNodeInstances.setState(ProcessInstanceState.TERMINATED);
    } else {
      // Terminate the specific element instanceToContinue in the process instanceToContinue
      FlowNodeInstance<?> instance =
          storedFlowNodeInstancesWrapper.getInstanceWithInstanceId(
              trigger.getElementInstanceIdPath().getFirst());
      if (instance != null) {
        FlowNodeInstanceProcessor<?, ?, ?> processor =
            flowNodeInstanceProcessorProvider.getProcessor(instance.getFlowNode());
        processor.processTerminate(
            processInstanceProcessingContext,
            flowNodeInstanceProcessingContext,
            instance,
            parentVariableScope);
      }
    }
    flowNodeInstances.determineImplicitCompletedState();
  }

  protected void continueNewInstances(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      VariableScope parentVariableScope) {

    flowInstanceRunner.continueNewInstances(
        processInstanceProcessingContext, flowNodeInstanceProcessingContext, parentVariableScope);

    DirectInstanceResult directInstanceResult =
        flowNodeInstanceProcessingContext.getDirectInstanceResult();

    if (directInstanceResult.getTerminateParent()) {
      List<Long> instancePath =
          pathExtractor.getInstancePath(
              flowNodeInstanceProcessingContext.getFlowNodeInstances().getParentFlowNodeInstance());
      if (!instancePath.isEmpty()) {
        instancePath.removeLast();
      }
      processInstanceProcessingContext
          .getInstanceResult()
          .addTerminateCommand(
              new TerminateTriggerDTO(
                  processInstanceProcessingContext.getProcessInstance().getProcessInstanceKey(),
                  instancePath));
    }

    EventSignal eventSignal = directInstanceResult.pollBubbleUpEvent();
    while (eventSignal != null) {
      processInstanceProcessingContext.getInstanceResult().addBubbleUpEvent(eventSignal);
      eventSignal = directInstanceResult.pollBubbleUpEvent();
    }
  }
}
