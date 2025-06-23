/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.ReceiveTaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ReceiveTask extends Activity implements WithMessageReference {
  private String messageRef;

  @Setter private Message referencedMessage;

  @Override
  public ActivityInstance<?> newActivityInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new ReceiveTaskInstance(parentInstance, this, elementInstanceId);
  }
}
