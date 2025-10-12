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

/**
 * An interface for consuming batches of ExternalTaskTriggerDTOs associated with specific job IDs.
 */
public interface ExternalTaskTriggerConsumer {

  /**
   * Retrieves the set of job IDs that this consumer is interested in.
   *
   * @return A set of job ID strings.
   */
  Set<String> getJobIds();

  /**
   * Accepts a batch of ExternalTaskTriggerDTOs for processing.
   *
   * @param batch A list of ExternalTaskTriggerDTOs to be processed.
   */
  void acceptBatch(List<ExternalTaskTriggerDTO> batch);
}
