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

import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.ProcessInstance;
import lombok.Getter;

@Getter
public class ProcessInstanceException extends RuntimeException {

  private final transient ProcessInstance processInstance;
  private final transient FlowNodeInstance<?> flowNodeInstance;

  public ProcessInstanceException(
      ProcessInstance processInstance, FlowNodeInstance<?> flowNodeInstance, String message) {
    super(message);
    this.processInstance = processInstance;
    this.flowNodeInstance = flowNodeInstance;
  }
}
