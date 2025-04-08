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

import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pd.model.ThrowEvent;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
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
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      String inputFlowId,
      VariableScope variables) {
    flowNodeInstance
        .getFlowNode()
        .getLinkventDefinition()
        .ifPresent(
            linkEventDefinition -> {
              Optional<IntermediateCatchEvent> intermediateCatchEvent =
                  flowElements.getIntermediateCatchEventWithName(linkEventDefinition.getName());
              intermediateCatchEvent.ifPresent(
                  event -> {
                    ProcessInstance processInstance = processingContext.getProcessInstance();
                    FlowNodeInstance<?> catchEventInstance =
                        new IntermediateCatchEventInstance(
                            flowNodeInstance.getParentInstance(),
                            event,
                            processInstance.getFlowNodeInstances().nextElementInstanceId());
                    catchEventInstance.setInitialState();
                    FlowNodeInstanceInfo flowNodeInstanceInfo =
                        new FlowNodeInstanceInfo(catchEventInstance, null);
                    directInstanceResult.addNewFlowNodeInstance(
                        processInstance, flowNodeInstanceInfo);
                  });
            });
    processStartSpecificThrowEventInstance(
        processingContext, directInstanceResult, flowElements, flowNodeInstance, variables);
  }

  protected abstract void processStartSpecificThrowEventInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      I flowNodeInstance,
      VariableScope variables);
}
