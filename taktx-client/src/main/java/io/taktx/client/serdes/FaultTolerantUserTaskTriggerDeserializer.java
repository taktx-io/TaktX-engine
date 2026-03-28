/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
