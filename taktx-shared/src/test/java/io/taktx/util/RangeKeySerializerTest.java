/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.FlowNodeInstanceKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.VariableKeyDTO;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RangeKeySerializerTest {

  @Test
  void processDefinitionKeySerializer_ordersVersionsByteLexicographically() {
    ProcessDefinitionKeySerializer serializer = new ProcessDefinitionKeySerializer();

    byte[] version1 = serializer.serialize("topic", new ProcessDefinitionKey("proc", 1));
    byte[] version2 = serializer.serialize("topic", new ProcessDefinitionKey("proc", 2));
    byte[] version100 = serializer.serialize("topic", new ProcessDefinitionKey("proc", 100));

    assertThat(Arrays.compareUnsigned(version1, version2)).isLessThan(0);
    assertThat(Arrays.compareUnsigned(version2, version100)).isLessThan(0);
  }

  @Test
  void flowNodeInstanceKeySerializer_ordersSiblingPathsAndProcessInstances() {
    FlowNodeInstanceKeySerializer serializer = new FlowNodeInstanceKeySerializer();
    UUID processInstanceX = new UUID(0L, 1L);
    UUID processInstanceY = new UUID(0L, 2L);

    byte[] x123 =
        serializer.serialize(
            "topic", new FlowNodeInstanceKeyDTO(processInstanceX, List.of(1L, 2L, 3L)));
    byte[] x124 =
        serializer.serialize(
            "topic", new FlowNodeInstanceKeyDTO(processInstanceX, List.of(1L, 2L, 4L)));
    byte[] y123 =
        serializer.serialize(
            "topic", new FlowNodeInstanceKeyDTO(processInstanceY, List.of(1L, 2L, 3L)));

    assertThat(Arrays.compareUnsigned(x123, x124)).isLessThan(0);
    assertThat(Arrays.compareUnsigned(x124, y123)).isLessThan(0);
  }

  @Test
  void variableKeySerializer_roundTripsAllFields() {
    VariableKeySerde serde = new VariableKeySerde();
    VariableKeyDTO original =
        new VariableKeyDTO(
            new FlowNodeInstanceKeyDTO(new UUID(42L, 99L), List.of(7L, 8L, 9L)), "customerStatus");

    byte[] bytes = serde.serializer().serialize("topic", original);
    VariableKeyDTO decoded = serde.deserializer().deserialize("topic", bytes);

    assertThat(decoded).isEqualTo(original);
  }
}
