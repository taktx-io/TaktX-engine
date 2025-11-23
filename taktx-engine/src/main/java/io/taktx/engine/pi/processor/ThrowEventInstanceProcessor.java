/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.StartFlowNodeInstanceInfo;
import io.taktx.engine.pi.model.ThrowEventInstance;
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
              String name =
                  feelExpressionHandler
                      .processFeelExpression(
                          signalEventDefinition.getReferencedSignal().name(),
                          scope.getVariableScope())
                      .asText();

              processInstanceProcessingContext.getInstanceResult().addSignal(name);
            });

    flowNodeInstance
        .getFlowNode()
        .getErrorEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              EventSignal errorEvent =
                  new ErrorEventSignal(
                      flowNodeInstance, errorEventDefinition.getReferencedError().code(), "");
              scope.getDirectInstanceResult().addEvent(errorEvent);
            });

    flowNodeInstance
        .getFlowNode()
        .getEscalationEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              EventSignal errorEvent =
                  new EscalationEventSignal(
                      flowNodeInstance,
                      errorEventDefinition.getReferencedEscalation().escalationCode(),
                      "");
              scope.getDirectInstanceResult().addEvent(errorEvent);
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
                    ProcessInstance processInstance =
                        processInstanceProcessingContext.getProcessInstance();
                    FlowNodeInstance<?> catchEventInstance =
                        new IntermediateCatchEventInstance(
                            flowNodeInstance.getParentInstance(),
                            event,
                            processInstance.getScope().nextElementInstanceId());
                    StartFlowNodeInstanceInfo startFlowNodeInstanceInfo =
                        new StartFlowNodeInstanceInfo(catchEventInstance, null);
                    scope
                        .getDirectInstanceResult()
                        .addNewFlowNodeInstance(startFlowNodeInstanceInfo);
                  });
            });

    processStartSpecificThrowEventInstance(
        processInstanceProcessingContext, scope, flowNodeInstance);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      I flowNodeInstance);
}
