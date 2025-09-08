/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.app;

import io.taktx.dto.FlowNodeInstanceUpdateDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import java.util.UUID;

public interface InstanceUpdateConsumer {

  void processInstanceUpdate(
      long timestamp, UUID processInstanceId, ProcessInstanceUpdateDTO update);

  void flowNodeInstanceUpdate(
      long timestamp, UUID processInstanceId, FlowNodeInstanceUpdateDTO update);
}
