/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.serdes.DeserializationResult;
import io.taktx.serdes.FaultTolerantProtoDeserializer;
import io.taktx.serdes.WorkerTriggerProtoMapper;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Fault-tolerant deserializer for {@link ExternalTaskTriggerDTO}.
 *
 * <p>Always decodes the protobuf body first. Signature verification is attempted afterwards and, if
 * it fails, the decoded body is still returned inside a {@link DeserializationResult} so the
 * consumer can use the {@code processInstanceId} and {@code elementInstanceIdPath} to report a BPMN
 * error or incident back to the engine rather than leaving the task silently stuck.
 */
public class FaultTolerantExternalTaskTriggerDeserializer
    implements Deserializer<DeserializationResult<ExternalTaskTriggerDTO>> {

  private static final Class<ExternalTaskTriggerDTO> CLAZZ = ExternalTaskTriggerDTO.class;

  private final FaultTolerantProtoDeserializer<io.taktx.proto.ExternalTaskTriggerMessage> delegate =
      new FaultTolerantProtoDeserializer<>(io.taktx.proto.ExternalTaskTriggerMessage.class, true) {
        @Override
        protected Parser<io.taktx.proto.ExternalTaskTriggerMessage> parser() {
          return io.taktx.proto.ExternalTaskTriggerMessage.parser();
        }
      };

  /** Creates a fault-tolerant external-task trigger deserializer. */
  public FaultTolerantExternalTaskTriggerDeserializer() {
    // no-op
  }

  /**
   * Returns the DTO class produced by this deserializer.
   *
   * @return {@link ExternalTaskTriggerDTO}.class
   */
  public Class<ExternalTaskTriggerDTO> getClazz() {
    return CLAZZ;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public DeserializationResult<ExternalTaskTriggerDTO> deserialize(String topic, byte[] data) {
    return map(delegate.deserialize(topic, data));
  }

  @Override
  public DeserializationResult<ExternalTaskTriggerDTO> deserialize(
      String topic, Headers headers, byte[] data) {
    return map(delegate.deserialize(topic, headers, data));
  }

  @Override
  public void close() {
    delegate.close();
  }

  private static DeserializationResult<ExternalTaskTriggerDTO> map(
      DeserializationResult<io.taktx.proto.ExternalTaskTriggerMessage> result) {
    if (result == null) {
      return null;
    }
    if (!result.hasValue()) {
      return DeserializationResult.failure(result.getError());
    }
    io.taktx.proto.ExternalTaskTriggerMessage message = result.getValue();
    if (message == null) {
      return DeserializationResult.failure(result.getError());
    }
    ExternalTaskTriggerDTO dto = WorkerTriggerProtoMapper.toDto(message);
    return result.isSuccess()
        ? DeserializationResult.success(dto)
        : DeserializationResult.bodyDecodedWithError(dto, result.getError());
  }
}
