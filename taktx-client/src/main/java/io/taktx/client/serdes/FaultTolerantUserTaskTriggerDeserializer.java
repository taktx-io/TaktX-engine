/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.serdes.DeserializationResult;
import io.taktx.serdes.FaultTolerantProtoDeserializer;
import io.taktx.serdes.WorkerTriggerProtoMapper;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Fault-tolerant deserializer for {@link UserTaskTriggerDTO}.
 *
 * <p>Decodes the protobuf body first, then verifies the signature independently. A signature
 * failure returns a {@link io.taktx.serdes.DeserializationResult} that still carries the decoded
 * payload so the consumer can route a BPMN error back to the correct process instance.
 */
public class FaultTolerantUserTaskTriggerDeserializer
    implements Deserializer<DeserializationResult<UserTaskTriggerDTO>> {

  private static final Class<UserTaskTriggerDTO> CLAZZ = UserTaskTriggerDTO.class;

  private final FaultTolerantProtoDeserializer<io.taktx.proto.UserTaskTriggerMessage> delegate =
      new FaultTolerantProtoDeserializer<>(io.taktx.proto.UserTaskTriggerMessage.class, true) {
        @Override
        protected Parser<io.taktx.proto.UserTaskTriggerMessage> parser() {
          return io.taktx.proto.UserTaskTriggerMessage.parser();
        }
      };

  public FaultTolerantUserTaskTriggerDeserializer() {
    // no-op
  }

  public Class<UserTaskTriggerDTO> getClazz() {
    return CLAZZ;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public DeserializationResult<UserTaskTriggerDTO> deserialize(String topic, byte[] data) {
    return map(delegate.deserialize(topic, data));
  }

  @Override
  public DeserializationResult<UserTaskTriggerDTO> deserialize(
      String topic, Headers headers, byte[] data) {
    return map(delegate.deserialize(topic, headers, data));
  }

  @Override
  public void close() {
    delegate.close();
  }

  private static DeserializationResult<UserTaskTriggerDTO> map(
      DeserializationResult<io.taktx.proto.UserTaskTriggerMessage> result) {
    if (result == null) {
      return null;
    }
    if (!result.hasValue()) {
      return DeserializationResult.failure(result.getError());
    }
    io.taktx.proto.UserTaskTriggerMessage message = result.getValue();
    if (message == null) {
      return DeserializationResult.failure(result.getError());
    }
    UserTaskTriggerDTO dto = WorkerTriggerProtoMapper.toDto(message);
    return result.isSuccess()
        ? DeserializationResult.success(dto)
        : DeserializationResult.bodyDecodedWithError(dto, result.getError());
  }
}
