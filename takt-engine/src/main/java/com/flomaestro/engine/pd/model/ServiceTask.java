/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.ServiceTaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ServiceTask extends ExternalTask {

  @Override
  public ActivityInstance<?> newActivityInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new ServiceTaskInstance(parentInstance, this, elementInstanceId);
  }
}
