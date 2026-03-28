/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
