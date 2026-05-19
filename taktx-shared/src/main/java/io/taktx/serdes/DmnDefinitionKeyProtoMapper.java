/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.DmnDefinitionKey;
import io.taktx.proto.DmnDefinitionKeyMessage;

/** Shared DTO ↔ protobuf mapper for DMN definition keys. */
public final class DmnDefinitionKeyProtoMapper {

  private DmnDefinitionKeyProtoMapper() {}

  public static DmnDefinitionKeyMessage toProto(DmnDefinitionKey dto) {
    DmnDefinitionKeyMessage.Builder builder = DmnDefinitionKeyMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getDmnDefinitionId() != null) {
      builder.setDmnDefinitionId(dto.getDmnDefinitionId());
    }
    if (dto.getVersion() != null) {
      builder.setVersion(dto.getVersion());
    }
    return builder.build();
  }

  public static DmnDefinitionKey toDto(DmnDefinitionKeyMessage message) {
    if (message == null) {
      return null;
    }
    return new DmnDefinitionKey(emptyToNull(message.getDmnDefinitionId()), message.getVersion());
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
