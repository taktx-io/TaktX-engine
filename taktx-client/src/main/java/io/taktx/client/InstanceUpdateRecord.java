/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
  private final int kafkaPartition;
  private final long kafkaOffset;

  /**
   * Create a new InstanceUpdateRecord.
   *
   * @param timestamp the epoch millis timestamp when the update occurred
   * @param processInstanceId the id of the process instance affected
   * @param update the details of the update
   * @param kafkaPartition the Kafka partition from which this record was consumed
   * @param kafkaOffset the Kafka offset of this record
   */
  public InstanceUpdateRecord(
      long timestamp,
      UUID processInstanceId,
      InstanceUpdateDTO update,
      int kafkaPartition,
      long kafkaOffset) {
    this.timestamp = timestamp;
    this.processInstanceId = processInstanceId;
    this.update = update;
    this.kafkaPartition = kafkaPartition;
    this.kafkaOffset = kafkaOffset;
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

  /**
   * Get the Kafka partition from which this record was consumed.
   *
   * @return the Kafka partition number
   */
  public int getKafkaPartition() {
    return kafkaPartition;
  }

  /**
   * Get the Kafka offset of this record.
   *
   * @return the Kafka offset
   */
  public long getKafkaOffset() {
    return kafkaOffset;
  }

  @Override
  public String toString() {
    return "InstanceUpdateRecord{"
        + "timestamp="
        + timestamp
        + ", processInstanceId="
        + processInstanceId
        + ", update="
        + update
        + ", kafkaPartition="
        + kafkaPartition
        + ", kafkaOffset="
        + kafkaOffset
        + '}';
  }
}
