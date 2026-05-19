/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.ConfigurationEventDTO;
import io.taktx.proto.ConfigurationEventMessage;

/** Deserializes protobuf-backed configuration events into legacy DTOs. */
public class ConfigurationEventDtoDeserializer
    extends ProtoDtoDeserializer<ConfigurationEventDTO, ConfigurationEventMessage> {

  public ConfigurationEventDtoDeserializer() {
    super(ConfigurationEventDTO.class, false);
  }

  @Override
  protected Parser<ConfigurationEventMessage> parser() {
    return ConfigurationEventMessage.parser();
  }

  @Override
  protected ConfigurationEventDTO toDto(ConfigurationEventMessage message) {
    return ConfigurationProtoMapper.toDto(message);
  }
}
