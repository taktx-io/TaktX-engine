/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.client.serdes;

import io.taktx.dto.MessageEventDTO;
import io.taktx.serdes.JsonSerializer;

/** A JSON serializer for MessageEventDTO objects. */
public class MessageEventSerializer extends JsonSerializer<MessageEventDTO> {

  /** Constructor for MessageEventSerializer. */
  public MessageEventSerializer() {
    super(MessageEventDTO.class);
  }
}
