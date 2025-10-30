/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client;

import io.taktx.dto.InstanceUpdateDTO;
import java.util.UUID;

/**
 * A record representing an update to a process instance scope or flow node instance, including the
 * timestamp of the update,the ID of the process instance, and the details of the update.
 */
public class InstanceUpdateRecord {

  private final long timestamp;
  private final UUID processInstanceId;
  private final InstanceUpdateDTO update;

  /**
   * Create a new InstanceUpdateRecord.
   *
   * @param timestamp the epoch millis timestamp when the update occurred
   * @param processInstanceId the id of the process instance affected
   * @param update the details of the update
   */
  public InstanceUpdateRecord(long timestamp, UUID processInstanceId, InstanceUpdateDTO update) {
    this.timestamp = timestamp;
    this.processInstanceId = processInstanceId;
    this.update = update;
  }

  /**
   * Get the timestamp of the update.
   *
   * @return the epoch millis timestamp when the update occurred
   */
  public long getTimestamp() {
    return this.timestamp;
  }

  /**
   * Get the ID of the process instance affected.
   *
   * @return the id of the process instance
   */
  public UUID getProcessInstanceId() {
    return this.processInstanceId;
  }

  /**
   * Get the details of the update.
   *
   * @return the InstanceUpdateDTO containing the update details
   */
  public InstanceUpdateDTO getUpdate() {
    return this.update;
  }
}
