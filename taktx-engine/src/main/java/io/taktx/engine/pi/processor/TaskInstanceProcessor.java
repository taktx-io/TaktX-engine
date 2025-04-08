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

import io.taktx.dto.v_1_0_0.ActtivityStateEnum;
import io.taktx.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.FlowElements;
import io.taktx.engine.pd.model.Task;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessingContext;
import io.taktx.engine.pi.model.TaskInstance;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class TaskInstanceProcessor
    extends ActivityInstanceProcessor<Task, TaskInstance, ContinueFlowElementTriggerDTO> {

  @Inject
  public TaskInstanceProcessor(
      FeelExpressionHandler feelExpressionHandler,
      IoMappingProcessor ioMappingProcessor,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(feelExpressionHandler, ioMappingProcessor, processInstanceMapper, clock);
  }

  @Override
  protected void processTerminateSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      TaskInstance instance,
      VariableScope processInstanceVariables) {
    // Nothing to do here
  }

  @Override
  protected void processStartSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      FlowElements flowElements,
      TaskInstance flowNodeInstance,
      String inputFlowId,
      VariableScope variables) {
    flowNodeInstance.setState(ActtivityStateEnum.FINISHED);
  }

  @Override
  protected void processContinueSpecificActivityInstance(
      ProcessingContext processingContext,
      DirectInstanceResult directInstanceResult,
      int subProcessLevel,
      FlowElements flowElements,
      TaskInstance externalTaskInstance,
      ContinueFlowElementTriggerDTO trigger,
      VariableScope processInstanceVariables) {
    // Nothing to do here
  }
}
