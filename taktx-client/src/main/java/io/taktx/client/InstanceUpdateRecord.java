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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A record representing an update to a process instance scope or flow node instance, including the
 * timestamp of the update,the ID of the process instance, and the details of the update.
 */
@RequiredArgsConstructor
@Getter
public class InstanceUpdateRecord {
  private final long timestamp;
  private final UUID processInstanceId;
  private final InstanceUpdateDTO update;
}
