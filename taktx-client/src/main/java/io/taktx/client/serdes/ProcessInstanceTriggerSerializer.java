/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import org.apache.kafka.common.serialization.Serializer;

/** A protobuf serializer for {@link ProcessInstanceTriggerDTO} objects. */
public class ProcessInstanceTriggerSerializer implements Serializer<ProcessInstanceTriggerDTO> {

  public Class<ProcessInstanceTriggerDTO> getClazz() {
    return ProcessInstanceTriggerDTO.class;
  }

  @Override
  public byte[] serialize(String topic, ProcessInstanceTriggerDTO data) {
    if (data == null) {
      return null;
    }
    return ProcessInstanceTriggerProtoMapper.toProto(data).toByteArray();
  }
}
