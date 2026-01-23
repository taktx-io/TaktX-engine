/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.VariablesDTO;
import io.taktx.engine.pd.model.EventSignal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EscalationEventSignal extends EventSignal {

  private String code;
  private String message;

  public EscalationEventSignal(
      FlowNodeInstance<?> fLowNodeInstance, String code, String message, VariablesDTO variables) {
    super(fLowNodeInstance, variables);
    this.code = code;
    this.message = message;
  }

  @Override
  public boolean shouldBubbleUp() {
    return true;
  }
}
