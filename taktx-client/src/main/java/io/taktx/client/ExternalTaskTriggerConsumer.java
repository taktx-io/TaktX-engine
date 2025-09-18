/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.dto.ExternalTaskTriggerDTO;
import java.util.List;
import java.util.Set;

public interface ExternalTaskTriggerConsumer {

  Set<String> getJobIds();

  void acceptBatch(List<ExternalTaskTriggerDTO> batch);
}
