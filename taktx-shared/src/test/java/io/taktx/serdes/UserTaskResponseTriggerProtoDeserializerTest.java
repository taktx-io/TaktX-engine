/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.ExternalTaskResponseResultDTO;
import io.taktx.dto.ExternalTaskResponseTriggerDTO;
import io.taktx.dto.ExternalTaskResponseType;
import io.taktx.dto.UserTaskResponseResultDTO;
import io.taktx.dto.UserTaskResponseTriggerDTO;
import io.taktx.dto.UserTaskResponseType;
import io.taktx.dto.VariablesDTO;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class UserTaskResponseTriggerProtoDeserializerTest {

  private static final String TOPIC = "usertasks-response";

  @Test
  void userTaskResponse_roundTripsFromProcessInstanceTriggerEnvelope() {
    UserTaskResponseTriggerDTO dto =
        new UserTaskResponseTriggerDTO(
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            List.of(10L, 20L),
            "message-1",
            new UserTaskResponseResultDTO(
                UserTaskResponseType.COMPLETED, null, "completed-by-worker"),
            VariablesDTO.of("approved", true, "reviewer", "alice"));

    byte[] payload = ProcessInstanceTriggerProtoMapper.toProto(dto).toByteArray();
    UserTaskResponseTriggerDTO roundTripped;
    try (UserTaskResponseTriggerProtoDeserializer deserializer =
        new UserTaskResponseTriggerProtoDeserializer()) {
      roundTripped = deserializer.deserialize(TOPIC, payload);
    }

    assertThat(roundTripped).usingRecursiveComparison().isEqualTo(dto);
  }

  @Test
  void externalTaskResponsePayload_throwsSerializationException() {
    ExternalTaskResponseTriggerDTO dto =
        new ExternalTaskResponseTriggerDTO(
            UUID.fromString("88888888-8888-8888-8888-888888888888"),
            List.of(30L),
            "message-2",
            new ExternalTaskResponseResultDTO(
                ExternalTaskResponseType.SUCCESS, true, null, null, 0L),
            VariablesDTO.of("status", "ok"));

    byte[] payload = ProcessInstanceTriggerProtoMapper.toProto(dto).toByteArray();

    try (UserTaskResponseTriggerProtoDeserializer deserializer =
        new UserTaskResponseTriggerProtoDeserializer()) {
      assertThatThrownBy(() -> deserialize(deserializer, payload))
          .isInstanceOf(SerializationException.class)
          .hasMessageContaining("Expected UserTaskResponseTriggerDTO");
    }
  }

  private static UserTaskResponseTriggerDTO deserialize(
      UserTaskResponseTriggerProtoDeserializer deserializer, byte[] payload) {
    return deserializer.deserialize(TOPIC, payload);
  }
}
