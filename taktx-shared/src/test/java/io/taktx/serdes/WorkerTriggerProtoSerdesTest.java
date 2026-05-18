/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerTriggerProtoSerdesTest {

  private static final String TOPIC = "worker-trigger-topic";

  @Test
  void externalTaskTrigger_roundTripsThroughProtoSerde() {
    ExternalTaskTriggerDTO dto =
        new ExternalTaskTriggerDTO(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            new ProcessDefinitionKey("service-task", 3),
            "payment-worker",
            "serviceTask",
            List.of(4L, 8L),
            VariablesDTO.of("status", "ok"),
            Map.of("worker", "billing", "priority", "high"));

    byte[] bytes;
    ExternalTaskTriggerDTO roundTripped;
    try (ExternalTaskTriggerProtoSerializer serializer = new ExternalTaskTriggerProtoSerializer();
        ExternalTaskTriggerProtoDeserializer deserializer =
            new ExternalTaskTriggerProtoDeserializer()) {
      bytes = serializer.serialize(TOPIC, dto);
      roundTripped = deserializer.deserialize(TOPIC, bytes);
    }

    assertThat(bytes).isNotNull().isNotEmpty();
    assertThat(roundTripped).isEqualTo(dto);
    assertThat(roundTripped.getHeaders()).containsEntry("worker", "billing");
    assertThat(roundTripped.getVariables()).isEqualTo(dto.getVariables());
  }

  @Test
  void userTaskTrigger_roundTripsThroughProtoSerde() {
    UserTaskTriggerDTO dto =
        new UserTaskTriggerDTO(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new ProcessDefinitionKey("review", -1),
            "approve-order",
            List.of(1L, 2L, 3L),
            new AssignmentDefinitionDTO("demo", "sales", "jane"),
            new TaskScheduleDTO("2026-05-19", "2026-05-20"),
            new PriorityDefinitionDTO("50"),
            VariablesDTO.of("amount", 100L));

    byte[] bytes;
    UserTaskTriggerDTO roundTripped;
    try (UserTaskTriggerProtoSerializer serializer = new UserTaskTriggerProtoSerializer();
        UserTaskTriggerProtoDeserializer deserializer = new UserTaskTriggerProtoDeserializer()) {
      bytes = serializer.serialize(TOPIC, dto);
      roundTripped = deserializer.deserialize(TOPIC, bytes);
    }

    assertThat(bytes).isNotNull().isNotEmpty();
    assertThat(roundTripped).isEqualTo(dto);
    assertThat(roundTripped.getAssignmentDefinition()).isEqualTo(dto.getAssignmentDefinition());
    assertThat(roundTripped.getTaskSchedule()).isEqualTo(dto.getTaskSchedule());
    assertThat(roundTripped.getPriorityDefinition()).isEqualTo(dto.getPriorityDefinition());
  }

  @Test
  void serializerAndDeserializer_handleTombstones() {
    try (ExternalTaskTriggerProtoSerializer serializer = new ExternalTaskTriggerProtoSerializer();
        ExternalTaskTriggerProtoDeserializer deserializer =
            new ExternalTaskTriggerProtoDeserializer()) {
      assertThat(serializer.serialize(TOPIC, null)).isNull();
      assertThat(deserializer.deserialize(TOPIC, null)).isNull();
    }
  }
}

