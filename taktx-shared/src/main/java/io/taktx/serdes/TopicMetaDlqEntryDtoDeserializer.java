/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.Parser;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import io.taktx.proto.TopicMetaDlqEntryMessage;

/** Deserializes protobuf-backed topic-meta DLQ entries into DTOs. */
public class TopicMetaDlqEntryDtoDeserializer
    extends ProtoDtoDeserializer<TopicMetaDlqEntryDTO, TopicMetaDlqEntryMessage> {

  public TopicMetaDlqEntryDtoDeserializer() {
    super(TopicMetaDlqEntryDTO.class, false);
  }

  @Override
  protected Parser<TopicMetaDlqEntryMessage> parser() {
    return TopicMetaDlqEntryMessage.parser();
  }

  @Override
  protected TopicMetaDlqEntryDTO toDto(TopicMetaDlqEntryMessage message) {
    return TopicMetaDlqEntryProtoMapper.toDto(message);
  }
}
