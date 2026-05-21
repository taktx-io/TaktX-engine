/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.proto.UserTaskTriggerMessage;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;

/** Kafka protobuf deserializer for {@link UserTaskTriggerDTO}. */
public class UserTaskTriggerProtoDeserializer implements Deserializer<UserTaskTriggerDTO> {

  private final ProtoDeserializer<UserTaskTriggerMessage> delegate =
      new ProtoDeserializer<>() {
        @Override
        protected Parser<UserTaskTriggerMessage> parser() {
          return UserTaskTriggerMessage.parser();
        }
      };

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    delegate.configure(configs, isKey);
  }

  @Override
  public UserTaskTriggerDTO deserialize(String topic, byte[] data) {
    UserTaskTriggerMessage message = delegate.deserialize(topic, data);
    return message == null ? null : WorkerTriggerProtoMapper.toDto(message);
  }

  @Override
  public void close() {
    delegate.close();
  }
}
