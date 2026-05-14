/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.CleanupPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageIdSerializationTest {

  private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

  @Test
  void externalTaskResponseTrigger_roundTripsMessageId() throws Exception {
    ExternalTaskResponseTriggerDTO original =
        new ExternalTaskResponseTriggerDTO(
            UUID.randomUUID(),
            List.of(1L, 2L),
            "msg-ext-1",
            new ExternalTaskResponseResultDTO(ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.empty());

    ExternalTaskResponseTriggerDTO deserialized =
        CBOR.readValue(CBOR.writeValueAsBytes(original), ExternalTaskResponseTriggerDTO.class);

    assertThat(deserialized.getMessageId()).isEqualTo("msg-ext-1");
    assertThat(deserialized.getExternalTaskResponseResult().getResponseType())
        .isEqualTo(ExternalTaskResponseType.SUCCESS);
  }

  @Test
  void userTaskResponseTrigger_roundTripsMessageId() throws Exception {
    UserTaskResponseTriggerDTO original =
        new UserTaskResponseTriggerDTO(
            UUID.randomUUID(),
            List.of(3L, 4L),
            "msg-user-1",
            new UserTaskResponseResultDTO(UserTaskResponseType.COMPLETED, null, null),
            VariablesDTO.empty());

    UserTaskResponseTriggerDTO deserialized =
        CBOR.readValue(CBOR.writeValueAsBytes(original), UserTaskResponseTriggerDTO.class);

    assertThat(deserialized.getMessageId()).isEqualTo("msg-user-1");
    assertThat(deserialized.getUserTaskResponseResult().getResponseType())
        .isEqualTo(UserTaskResponseType.COMPLETED);
  }

  @Test
  void topicMeta_roundTripsMessageId() throws Exception {
    TopicMetaDTO original =
        new TopicMetaDTO("tenant.ns.external-task-trigger.ship-order", 3, CleanupPolicy.DELETE, (short) 1, "msg-topic-1");

    TopicMetaDTO deserialized = CBOR.readValue(CBOR.writeValueAsBytes(original), TopicMetaDTO.class);

    assertThat(deserialized.getMessageId()).isEqualTo("msg-topic-1");
    assertThat(deserialized.getTopicName()).isEqualTo(original.getTopicName());
  }
}

