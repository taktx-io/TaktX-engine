/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.VariablesDTO;
import io.taktx.serdes.ExternalTaskTriggerProtoSerializer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExternalTaskTriggerDeserializerTest {
  @Test
  void decodesProtoPayloads() {
    ExternalTaskTriggerDTO dto =
        new ExternalTaskTriggerDTO(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            new ProcessDefinitionKey("shipping", 4),
            "ship-job",
            "shipTask",
            List.of(7L, 8L),
            VariablesDTO.of("approved", true),
            Map.of("worker", "warehouse"));

    try (ExternalTaskTriggerDeserializer deserializer = new ExternalTaskTriggerDeserializer();
        ExternalTaskTriggerProtoSerializer serializer = new ExternalTaskTriggerProtoSerializer()) {
      assertThat(
              deserializer.deserialize(
                  "external-task-trigger", serializer.serialize("external-task-trigger", dto)))
          .usingRecursiveComparison()
          .isEqualTo(dto);
      assertThat(deserializer.getClazz()).isEqualTo(ExternalTaskTriggerDTO.class);
    }
  }
}
