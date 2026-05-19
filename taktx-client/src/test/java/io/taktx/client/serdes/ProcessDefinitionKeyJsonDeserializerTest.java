/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.jackson.TaktxObjectMappers;
import io.taktx.util.ProcessDefinitionKeySerializer;
import org.junit.jupiter.api.Test;

class ProcessDefinitionKeyJsonDeserializerTest {

  @Test
  void deserializesCurrentBinaryKeyFormat() {
    ProcessDefinitionKey original = new ProcessDefinitionKey("order-process", 12);
    byte[] bytes = new ProcessDefinitionKeySerializer().serialize("topic", original);

    ProcessDefinitionKey decoded =
        new ProcessDefinitionKeyJsonDeserializer().deserialize("topic", bytes);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void deserializesLegacyCborKeyFormat() throws Exception {
    ProcessDefinitionKey original = new ProcessDefinitionKey("legacy-process", 3);
    byte[] bytes = TaktxObjectMappers.cbor().writeValueAsBytes(original);

    ProcessDefinitionKey decoded =
        new ProcessDefinitionKeyJsonDeserializer().deserialize("topic", bytes);

    assertThat(decoded).isEqualTo(original);
  }
}
