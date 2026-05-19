/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.taktx.CleanupPolicy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class TopicMetaDTO {
  private String topicName; // The actual Kafka topic name
  private int nrPartitions;
  private CleanupPolicy cleanupPolicy;
  private short replicationFactor;
  private String messageId;

  public TopicMetaDTO(
      String topicName, int nrPartitions, CleanupPolicy cleanupPolicy, short replicationFactor) {
    this(topicName, nrPartitions, cleanupPolicy, replicationFactor, null);
  }

  public TopicMetaDTO(
      String topicName,
      int nrPartitions,
      CleanupPolicy cleanupPolicy,
      short replicationFactor,
      String messageId) {
    this.topicName = topicName;
    this.nrPartitions = nrPartitions;
    this.cleanupPolicy = cleanupPolicy;
    this.replicationFactor = replicationFactor;
    this.messageId = messageId;
  }
}
