/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicRegistration {
  private String topicName; // The full topic name (with prefix if applicable)
  private boolean fixedTopic; // Indicates if this is a fixed system topic
  private int partitions;
  private short replicationFactor;
  private TopicStatus status;
  private Map<String, String> configs;
  private LocalDateTime requestedTime;
  private LocalDateTime createdTime;
  private String errorMessage;
}
