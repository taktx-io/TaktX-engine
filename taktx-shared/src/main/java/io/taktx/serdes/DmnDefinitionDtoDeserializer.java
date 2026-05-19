/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.DmnDefinitionDTO;
import io.taktx.proto.DmnDefinitionMessage;

/** Deserializes protobuf-backed DMN definitions into legacy DTOs. */
public class DmnDefinitionDtoDeserializer
    extends ProtoDtoDeserializer<DmnDefinitionDTO, DmnDefinitionMessage> {

  public DmnDefinitionDtoDeserializer() {
    super(DmnDefinitionDTO.class, false);
  }

  @Override
  protected Parser<DmnDefinitionMessage> parser() {
    return DmnDefinitionMessage.parser();
  }

  @Override
  protected DmnDefinitionDTO toDto(DmnDefinitionMessage message) {
    return DmnDefinitionsProtoMapper.toDto(message);
  }
}
