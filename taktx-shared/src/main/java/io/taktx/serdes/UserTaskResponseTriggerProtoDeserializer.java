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
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Deserializes protobuf process-instance trigger envelopes into {@link UserTaskResponseTriggerDTO}.
 */
public class UserTaskResponseTriggerProtoDeserializer
    implements Deserializer<UserTaskResponseTriggerDTO> {

  @Override
  public UserTaskResponseTriggerDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      ProcessInstanceTriggerDTO dto =
          ProcessInstanceTriggerProtoMapper.toDto(ProcessInstanceTriggerEnvelope.parseFrom(data));
      if (dto == null) {
        return null;
      }
      if (dto instanceof UserTaskResponseTriggerDTO userTaskResponseTriggerDTO) {
        return userTaskResponseTriggerDTO;
      }
      throw new SerializationException(
          "Expected UserTaskResponseTriggerDTO but decoded " + dto.getClass().getSimpleName());
    } catch (InvalidProtocolBufferException e) {
      throw new SerializationException("Failed to deserialize ProcessInstanceTriggerEnvelope", e);
    }
  }
}
