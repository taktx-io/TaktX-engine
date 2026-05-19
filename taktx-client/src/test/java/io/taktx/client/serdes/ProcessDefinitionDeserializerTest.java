/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ProcessDefinitionDTO;
import io.taktx.dto.ProcessDefinitionStateEnum;
import io.taktx.serdes.DefinitionsProtoMapper;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDeserializerTest {
  @Test
  void deserialize_readsProcessDefinitionMessage() {
    ProcessDefinitionDTO dto =
        new ProcessDefinitionDTO(
            new ParsedDefinitionsDTO(
                new DefinitionsKey("orders", "hash-123"),
                ProcessDTO.NONE,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()),
            3,
            ProcessDefinitionStateEnum.ACTIVE);

    try (ProcessDefinitionDeserializer deserializer = new ProcessDefinitionDeserializer()) {
      byte[] payload = DefinitionsProtoMapper.toProto(dto).toByteArray();

      assertThat(deserializer.deserialize("process-definitions", payload)).isEqualTo(dto);
    }
  }
}
