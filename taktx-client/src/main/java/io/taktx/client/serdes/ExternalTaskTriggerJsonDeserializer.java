/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.serdes.JsonDeserializer;

/** A JSON deserializer for ExternalTaskTriggerDTO objects. */
public class ExternalTaskTriggerJsonDeserializer extends JsonDeserializer<ExternalTaskTriggerDTO> {
  /** Constructor for ExternalTaskTriggerJsonDeserializer. */
  public ExternalTaskTriggerJsonDeserializer() {
    super(ExternalTaskTriggerDTO.class, true);
  }
}
