/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
