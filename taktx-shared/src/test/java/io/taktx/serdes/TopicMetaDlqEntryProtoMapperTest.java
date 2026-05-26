/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.CleanupPolicy;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.dto.TopicMetaDlqEntryDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopicMetaDlqEntryProtoMapperTest {

  @Test
  void roundTrip_preservesTopicMetaHeadersAndBinaryPayload() {
    Map<String, byte[]> headers = new LinkedHashMap<>();
    headers.put("x-request-id", new byte[] {1, 2, 3});
    headers.put("x-trace", new byte[] {4, 5});
    TopicMetaDlqEntryDTO dto =
        new TopicMetaDlqEntryDTO(
            "default.topic-meta",
            new TopicMetaDTO("default.topic-meta", 3, CleanupPolicy.COMPACT, (short) 2, "msg-1"),
            headers,
            new byte[] {9, 8, 7, 6});

    TopicMetaDlqEntryDTO restored =
        TopicMetaDlqEntryProtoMapper.toDto(TopicMetaDlqEntryProtoMapper.toProto(dto));

    assertThat(restored.getTopicName()).isEqualTo("default.topic-meta");
    assertThat(restored.getValue()).isEqualTo(dto.getValue());
    assertThat(restored.getHeaders()).hasSize(2);
    assertThat(restored.getHeaders().get("x-request-id")).containsExactly(1, 2, 3);
    assertThat(restored.getHeaders().get("x-trace")).containsExactly(4, 5);
    assertThat(restored.getData()).containsExactly(9, 8, 7, 6);
  }

  @Test
  void toProto_nullInput_returnsEmptyMessage() {
    io.taktx.proto.TopicMetaDlqEntryMessage message = TopicMetaDlqEntryProtoMapper.toProto(null);

    assertThat(message.getTopicName()).isEmpty();
    assertThat(message.hasValue()).isFalse();
    assertThat(message.getHeadersMap()).isEmpty();
    assertThat(message.getData().isEmpty()).isTrue();
  }

  @Test
  void toDto_emptyFieldsBecomeNulls() {
    io.taktx.proto.TopicMetaDlqEntryMessage message =
        io.taktx.proto.TopicMetaDlqEntryMessage.newBuilder().setTopicName("").build();

    TopicMetaDlqEntryDTO dto = TopicMetaDlqEntryProtoMapper.toDto(message);

    assertThat(dto.getTopicName()).isNull();
    assertThat(dto.getValue()).isNull();
    assertThat(dto.getHeaders()).isEmpty();
    assertThat(dto.getData()).isNull();
  }

  @Test
  void toDto_nullMessage_returnsNull() {
    assertThat(TopicMetaDlqEntryProtoMapper.toDto(null)).isNull();
  }
}
