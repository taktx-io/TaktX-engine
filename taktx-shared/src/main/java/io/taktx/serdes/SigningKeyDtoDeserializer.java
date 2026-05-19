/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.proto.SigningKeyMessage;

/** Deserializes protobuf-backed signing-key records into legacy DTOs. */
public class SigningKeyDtoDeserializer
    extends ProtoDtoDeserializer<SigningKeyDTO, SigningKeyMessage> {

  public SigningKeyDtoDeserializer() {
    super(SigningKeyDTO.class, false);
  }

  @Override
  protected Parser<SigningKeyMessage> parser() {
    return SigningKeyMessage.parser();
  }

  @Override
  protected SigningKeyDTO toDto(SigningKeyMessage message) {
    return SigningKeyProtoMapper.toDto(message);
  }
}
