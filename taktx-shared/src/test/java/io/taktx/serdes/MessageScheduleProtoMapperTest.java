/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.FixedRateMessageScheduleDTO;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.dto.OneTimeScheduleDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.RecurringMessageScheduleDTO;
import io.taktx.dto.StartCommandDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.MessageScheduleEnvelope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageScheduleProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through MessageScheduleEnvelope")
  @MethodSource("scheduleCases")
  void scheduleFamily_roundTripsThroughProtoEnvelope(String name, MessageScheduleDTO schedule)
      throws Exception {
    MessageScheduleEnvelope envelope = MessageScheduleProtoMapper.toProto(schedule);

    MessageScheduleEnvelope parsed = MessageScheduleEnvelope.parseFrom(envelope.toByteArray());
    MessageScheduleDTO restored = MessageScheduleProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(schedule);
  }

  private static Stream<Arguments> scheduleCases() {
    StartCommandDTO startCommand =
        new StartCommandDTO(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            null,
            null,
            new ProcessDefinitionKey("proc-a", 4),
            VariablesDTO.of("priority", "high", "count", 3L));

    ExternalTaskTriggerDTO externalTaskTrigger =
        new ExternalTaskTriggerDTO(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new ProcessDefinitionKey("proc-b", 9),
            "ext-17",
            "service-task",
            List.of(10L, 20L),
            VariablesDTO.of("payload", "retry"),
            Map.of("x-retry", "true"));

    return Stream.of(
        Arguments.of("oneTimeStartCommand", new OneTimeScheduleDTO(startCommand, 1_000L, 2_000L)),
        Arguments.of(
            "fixedRateExternalTask",
            new FixedRateMessageScheduleDTO(externalTaskTrigger, 5_000L, 3, 1_500L)),
        Arguments.of(
            "recurringStartCommand",
            new RecurringMessageScheduleDTO(startCommand, "0 0/5 * * * ?", 2_500L)));
  }
}



