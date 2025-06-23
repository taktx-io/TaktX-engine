/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.IntermediateCatchEvent;
import io.taktx.engine.pi.DirectInstanceResult;
import io.taktx.engine.pi.ProcessInstanceMapper;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.IntermediateCatchEventInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class IntermediateCatchEventInstanceProcessor
    extends CatchEventInstanceProcessor<IntermediateCatchEvent, IntermediateCatchEventInstance> {

  @Inject
  IntermediateCatchEventInstanceProcessor(
      IoMappingProcessor ioMappingProcessor,
      FeelExpressionHandler feelExpressionHandler,
      ProcessInstanceMapper processInstanceMapper,
      Clock clock) {
    super(ioMappingProcessor, processInstanceMapper, feelExpressionHandler, clock);
  }

  @Override
  protected boolean shoudHandleTimerEvents() {
    return true;
  }

  @Override
  protected void processContinueSpecificCatchEventInstance(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      DirectInstanceResult directInstanceResult,
      IntermediateCatchEventInstance flowNodeInstance) {
    // nothing to do
  }

  @Override
  protected boolean shouldCancel(IntermediateCatchEventInstance flowNodeInstance) {
    return true;
  }
}
