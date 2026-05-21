/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.XmlDefinitionsDTO;
import io.taktx.serdes.DefinitionsProtoMapper;
import org.apache.kafka.common.serialization.Serializer;

/** A protobuf serializer for {@link XmlDefinitionsDTO} objects. */
public class XmlDefinitionSerializer implements Serializer<XmlDefinitionsDTO> {

  @Override
  public byte[] serialize(String topic, XmlDefinitionsDTO data) {
    if (data == null) {
      return null;
    }
    return DefinitionsProtoMapper.toProto(data).toByteArray();
  }
}
