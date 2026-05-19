/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.proto.TopicMetaMessage;

/** Deserializes protobuf-backed topic metadata into legacy DTOs. */
public class TopicMetaDtoDeserializer extends ProtoDtoDeserializer<TopicMetaDTO, TopicMetaMessage> {

  public TopicMetaDtoDeserializer() {
    this(false);
  }

  protected TopicMetaDtoDeserializer(boolean shouldValidateSignature) {
    super(TopicMetaDTO.class, shouldValidateSignature);
  }

  @Override
  protected Parser<TopicMetaMessage> parser() {
    return TopicMetaMessage.parser();
  }

  @Override
  protected TopicMetaDTO toDto(TopicMetaMessage message) {
    return TopicMetaProtoMapper.toDto(message);
  }
}
