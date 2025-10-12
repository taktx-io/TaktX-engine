/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.serdes;

import io.taktx.dto.StartCommandDTO;
import io.taktx.serdes.JsonSerializer;

/** A JSON serializer for StartCommandDTO objects. */
public class StartCommandSerializer extends JsonSerializer<StartCommandDTO> {

  /** Constructor for StartCommandSerializer. */
  public StartCommandSerializer() {
    super(StartCommandDTO.class);
  }
}
