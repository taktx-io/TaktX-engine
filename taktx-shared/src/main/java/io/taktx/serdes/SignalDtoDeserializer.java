/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.SignalDTO;
import io.taktx.proto.SignalEnvelope;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/** Deserializes signal protobuf envelopes into DTOs. */
public class SignalDtoDeserializer implements Deserializer<SignalDTO> {

  @Override
  public SignalDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return SignalProtoMapper.toDto(SignalEnvelope.parseFrom(data));
    } catch (InvalidProtocolBufferException e) {
      throw new SerializationException("Failed to deserialize SignalEnvelope", e);
    }
  }
}
