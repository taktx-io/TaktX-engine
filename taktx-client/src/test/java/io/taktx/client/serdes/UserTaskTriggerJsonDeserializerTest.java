/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.dto.PriorityDefinitionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.TaskScheduleDTO;
import io.taktx.dto.UserTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.serdes.UserTaskTriggerProtoSerializer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTaskTriggerJsonDeserializerTest {

  @Test
  void decodesProtoPayloadsViaLegacyAlias() {
    UserTaskTriggerDTO dto =
        new UserTaskTriggerDTO(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            new ProcessDefinitionKey("approval", 6),
            "approve-order",
            List.of(1L, 2L, 3L),
            new AssignmentDefinitionDTO("manager", "sales", "jane"),
            new TaskScheduleDTO("2026-05-19", "2026-05-20"),
            new PriorityDefinitionDTO("90"),
            VariablesDTO.of("amount", 42L));

    try (UserTaskTriggerJsonDeserializer deserializer = new UserTaskTriggerJsonDeserializer();
        UserTaskTriggerProtoSerializer serializer = new UserTaskTriggerProtoSerializer()) {
      assertThat(
              deserializer.deserialize(
                  "user-task-trigger", serializer.serialize("user-task-trigger", dto)))
          .usingRecursiveComparison()
          .isEqualTo(dto);
      assertThat(deserializer.getClazz()).isEqualTo(UserTaskTriggerDTO.class);
    }
  }
}
