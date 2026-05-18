/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes process-instance trigger protobuf envelopes into DTOs. */
public class ProcessInstanceTriggerDtoDeserializer
    implements Deserializer<ProcessInstanceTriggerDTO> {

  @Override
  public ProcessInstanceTriggerDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return ProcessInstanceTriggerProtoMapper.toDto(ProcessInstanceTriggerEnvelope.parseFrom(data));
    } catch (InvalidProtocolBufferException e) {
      throw new SerializationException("Failed to deserialize ProcessInstanceTriggerEnvelope", e);
    }
  }
}

