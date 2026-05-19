/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.serdes.DmnDefinitionsProtoMapper;
import io.taktx.serdes.ProtoMappedSerializer;

/** Backward-compatible serializer for protobuf-backed {@link XmlDmnDefinitionsDTO} objects. */
public class XmlDmnDefinitionSerializer extends ProtoMappedSerializer<XmlDmnDefinitionsDTO> {

  /** Constructor for XmlDmnDefinitionSerializer. */
  public XmlDmnDefinitionSerializer() {
    super(XmlDmnDefinitionsDTO.class);
  }

  @Override
  protected com.google.protobuf.MessageLite toProto(XmlDmnDefinitionsDTO data) {
    return DmnDefinitionsProtoMapper.toProto(data);
  }
}
