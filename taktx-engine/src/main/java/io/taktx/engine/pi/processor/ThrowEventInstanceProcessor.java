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

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pi.FlowNodeInstanceProcessingContext;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.ErrorEventSignal;
import io.taktx.engine.pi.model.EscalationEventSignal;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.FlowNodeInstanceInfo;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import io.taktx.engine.pi.model.ThrowEventInstance;
import io.taktx.engine.pi.model.VariableScope;
import java.time.Clock;
import java.util.Optional;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public abstract class ThrowEventInstanceProcessor<
        E extends ThrowEvent, I extends ThrowEventInstance<?>>
    extends EventInstanceProcessor<E, I> {

  protected ThrowEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processStartSpecificEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flowNodeInstance,
      String inputFlowId,
      VariableScope variables) {

    flowNodeInstance
        .getFlowNode()
        .getErrorEventDefinition()
        .ifPresent(
            errorEventDefinition -> {
              EventSignal errorEvent =
                  new ErrorEventSignal(
                      flowNodeInstance, errorEventDefinition.getReferencedError().code(), "");
              flowNodeInstanceProcessingContext.getDirectInstanceResult().addEvent(errorEvent);
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
              flowNodeInstanceProcessingContext.getDirectInstanceResult().addEvent(errorEvent);
            });

    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinition()
        .ifPresent(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent> intermediateCatchEvent =
                  flowNodeInstanceProcessingContext
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
                            processInstance.getFlowNodeInstances().nextElementInstanceId());
                    catchEventInstance.setInitialState();
                    FlowNodeInstanceInfo flowNodeInstanceInfo =
                        new FlowNodeInstanceInfo(catchEventInstance, null);
                    flowNodeInstanceProcessingContext
                        .getDirectInstanceResult()
                        .addNewFlowNodeInstance(processInstance, flowNodeInstanceInfo);
                  });
            });

    processStartSpecificThrowEventInstance(
        processInstanceProcessingContext,
        flowNodeInstanceProcessingContext,
        flowNodeInstance,
        variables);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      FlowNodeInstanceProcessingContext flowNodeInstanceProcessingContext,
      I flowNodeInstance,
      VariableScope variables);
}
