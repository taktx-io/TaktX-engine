/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.ByteString;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared DTO ↔ protobuf mapper for topic-meta DLQ entries. */
public final class TopicMetaDlqEntryProtoMapper {

  private TopicMetaDlqEntryProtoMapper() {}

  public static io.taktx.proto.TopicMetaDlqEntryMessage toProto(TopicMetaDlqEntryDTO dto) {
    io.taktx.proto.TopicMetaDlqEntryMessage.Builder builder =
        io.taktx.proto.TopicMetaDlqEntryMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getTopicName() != null) {
      builder.setTopicName(dto.getTopicName());
    }
    if (dto.getValue() != null) {
      builder.setValue(TopicMetaProtoMapper.toProto(dto.getValue()));
    }
    if (dto.getHeaders() != null) {
      dto.getHeaders().forEach((key, value) -> builder.putHeaders(key, ByteString.copyFrom(value)));
    }
    if (dto.getData() != null) {
      builder.setData(ByteString.copyFrom(dto.getData()));
    }
    return builder.build();
  }

  public static TopicMetaDlqEntryDTO toDto(io.taktx.proto.TopicMetaDlqEntryMessage message) {
    if (message == null) {
      return null;
    }
    return new TopicMetaDlqEntryDTO(
        emptyToNull(message.getTopicName()),
        message.hasValue() ? TopicMetaProtoMapper.toDto(message.getValue()) : null,
        toHeaders(message.getHeadersMap()),
        emptyBytesToNull(message.getData()));
  }

  private static Map<String, byte[]> toHeaders(Map<String, ByteString> headers) {
    Map<String, byte[]> mappedHeaders = new LinkedHashMap<>();
    headers.forEach(
        (key, value) -> mappedHeaders.put(key, value == null ? null : value.toByteArray()));
    return mappedHeaders;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static byte[] emptyBytesToNull(ByteString value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return value.toByteArray();
  }
}
