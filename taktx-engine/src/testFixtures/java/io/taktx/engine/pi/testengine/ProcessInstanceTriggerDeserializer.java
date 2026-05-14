/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.taktx.dto.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerDeserializer
    extends ObjectMapperDeserializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerDeserializer() {
    super(ProcessInstanceTriggerDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
