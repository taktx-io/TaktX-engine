/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.XmlDmnDefinitionsDTO;
import io.taktx.proto.XmlDmnDefinitionsMessage;

/** Deserializes protobuf-backed XML DMN definitions into legacy DTOs. */
public class XmlDmnDefinitionsDtoDeserializer
    extends ProtoDtoDeserializer<XmlDmnDefinitionsDTO, XmlDmnDefinitionsMessage> {

  public XmlDmnDefinitionsDtoDeserializer() {
    super(XmlDmnDefinitionsDTO.class, false);
  }

  @Override
  protected Parser<XmlDmnDefinitionsMessage> parser() {
    return XmlDmnDefinitionsMessage.parser();
  }

  @Override
  protected XmlDmnDefinitionsDTO toDto(XmlDmnDefinitionsMessage message) {
    return DmnDefinitionsProtoMapper.toDto(message);
  }
}
