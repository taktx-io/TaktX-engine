/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.serdes.DeserializationResult;
import io.taktx.serdes.FaultTolerantJsonDeserializer;

/**
 * Fault-tolerant deserializer for {@link ExternalTaskTriggerDTO}.
 *
 * <p>Always decodes the CBOR body first. Signature verification is attempted afterwards and, if it
 * fails, the decoded body is still returned inside a {@link DeserializationResult} so the consumer
 * can use the {@code processInstanceId} and {@code elementInstanceIdPath} to report a BPMN error or
 * incident back to the engine rather than leaving the task silently stuck.
 */
public class FaultTolerantExternalTaskTriggerDeserializer
    extends FaultTolerantJsonDeserializer<ExternalTaskTriggerDTO> {

  public FaultTolerantExternalTaskTriggerDeserializer() {
    super(ExternalTaskTriggerDTO.class, true);
  }
}
