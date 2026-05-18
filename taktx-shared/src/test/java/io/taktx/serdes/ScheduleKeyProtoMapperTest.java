/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DefinitionScheduleKeyDTO;
import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.dto.TimeBucket;
import io.taktx.proto.ScheduleKeyEnvelope;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduleKeyProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through ScheduleKeyEnvelope")
  @MethodSource("scheduleKeyCases")
  void scheduleKeyFamily_roundTripsThroughProtoEnvelope(String name, ScheduleKeyDTO scheduleKey)
      throws Exception {
    ScheduleKeyEnvelope envelope = ScheduleKeyProtoMapper.toProto(scheduleKey);

    ScheduleKeyEnvelope parsed = ScheduleKeyEnvelope.parseFrom(envelope.toByteArray());
    ScheduleKeyDTO restored = ScheduleKeyProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(scheduleKey);
  }

  private static Stream<Arguments> scheduleKeyCases() {
    return Stream.of(
        Arguments.of(
            "definitionScheduleKey",
            new DefinitionScheduleKeyDTO(
                new ProcessDefinitionKey("proc-a", 7), "timer-start", TimeBucket.HOURLY)),
        Arguments.of(
            "instanceScheduleKey",
            new InstanceScheduleKeyDTO(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                List.of(1L, 2L, 3L),
                "boundary-timer",
                TimeBucket.MINUTE)));
  }
}
