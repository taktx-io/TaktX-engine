/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client.serdes;

import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.serdes.FaultTolerantJsonDeserializer;

/**
 * Fault-tolerant deserializer for {@link UserTaskTriggerDTO}.
 *
 * <p>Decodes the CBOR body first, then verifies the signature independently. A signature failure
 * returns a {@link io.taktx.serdes.DeserializationResult} that still carries the decoded payload so
 * the consumer can route a BPMN error back to the correct process instance.
 */
public class FaultTolerantUserTaskTriggerDeserializer
    extends FaultTolerantJsonDeserializer<UserTaskTriggerDTO> {

  public FaultTolerantUserTaskTriggerDeserializer() {
    super(UserTaskTriggerDTO.class, true);
  }
}
