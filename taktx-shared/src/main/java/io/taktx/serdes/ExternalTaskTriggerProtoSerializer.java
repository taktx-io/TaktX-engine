/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.ExternalTaskTriggerDTO;
import org.apache.kafka.common.serialization.Serializer;

/** Kafka protobuf serializer for {@link ExternalTaskTriggerDTO}. */
public class ExternalTaskTriggerProtoSerializer implements Serializer<ExternalTaskTriggerDTO> {

  @Override
  public byte[] serialize(String topic, ExternalTaskTriggerDTO data) {
    return data == null ? null : WorkerTriggerProtoMapper.toProto(data).toByteArray();
  }
}
