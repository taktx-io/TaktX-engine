/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.MessageLite;
import io.taktx.dto.TopicMetaDlqEntryDTO;

/** Serializes topic-meta DLQ entries using the protobuf contract. */
public class TopicMetaDlqEntrySerializer extends ProtoMappedSerializer<TopicMetaDlqEntryDTO> {

  public TopicMetaDlqEntrySerializer() {
    super(TopicMetaDlqEntryDTO.class);
  }

  @Override
  protected MessageLite toProto(TopicMetaDlqEntryDTO data) {
    return TopicMetaDlqEntryProtoMapper.toProto(data);
  }
}
