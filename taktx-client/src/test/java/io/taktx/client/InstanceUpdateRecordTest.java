/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstanceUpdateRecordTest {

  @Test
  void constructor_storesAllFields() {
    UUID instanceId = UUID.randomUUID();
    long timestamp = 1_700_000_000_000L;
    InstanceUpdateRecord instanceUpdateRecord =
        new InstanceUpdateRecord(timestamp, instanceId, null, 3, 42L);

    assertThat(instanceUpdateRecord.getTimestamp()).isEqualTo(timestamp);
    assertThat(instanceUpdateRecord.getProcessInstanceId()).isEqualTo(instanceId);
    assertThat(instanceUpdateRecord.getUpdate()).isNull();
    assertThat(instanceUpdateRecord.getKafkaPartition()).isEqualTo(3);
    assertThat(instanceUpdateRecord.getKafkaOffset()).isEqualTo(42L);
  }

  @Test
  void toString_containsKeyFields() {
    UUID instanceId = UUID.randomUUID();
    InstanceUpdateRecord instanceUpdateRecord =
        new InstanceUpdateRecord(100L, instanceId, null, 0, 7L);
    String s = instanceUpdateRecord.toString();

    assertThat(s)
        .contains("timestamp=100")
        .contains(instanceId.toString())
        .contains("kafkaPartition=0")
        .contains("kafkaOffset=7");
  }
}
