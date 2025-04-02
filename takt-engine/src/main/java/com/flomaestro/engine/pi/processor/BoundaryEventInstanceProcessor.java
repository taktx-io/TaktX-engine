/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pi.processor;

import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.BoundaryEvent;
import com.flomaestro.engine.pi.DirectInstanceResult;
import com.flomaestro.engine.pi.InstanceResult;
import com.flomaestro.engine.pi.ProcessInstanceMapper;
import com.flomaestro.engine.pi.ProcessingStatistics;
import com.flomaestro.engine.pi.model.BoundaryEventInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class BoundaryEventInstanceProcessor
    extends CatchEventInstanceProcessor<BoundaryEvent, BoundaryEventInstance> {

  @Inject
  BoundaryEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected boolean shoudHandleTimerxEvents() {
    return true;
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      InstanceResult instanceResult,
      DirectInstanceResult directInstanceResult,
      BoundaryEventInstance boundaryEventInstance,
      ProcessingStatistics processingStatistics) {
    if (shouldCancel(boundaryEventInstance)) {
      directInstanceResult.addTerminateInstance(boundaryEventInstance.getAttachedInstanceId());
    }
  }

  @Override
  protected boolean shouldCancel(BoundaryEventInstance flowNodeInstance) {
    return flowNodeInstance.getFlowNode().isCancelActivity();
  }
}
