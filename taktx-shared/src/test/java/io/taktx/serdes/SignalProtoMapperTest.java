/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.CancelDefinitionSignalSubscriptionDTO;
import io.taktx.dto.CancelInstanceSignalSubscriptionDTO;
import io.taktx.dto.NewDefinitionSignalSubscriptionDTO;
import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.SignalDTO;
import io.taktx.proto.SignalEnvelope;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignalProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through SignalEnvelope")
  @MethodSource("signalCases")
  void signalFamily_roundTripsThroughProtoEnvelope(String name, SignalDTO signal) throws Exception {
    SignalEnvelope envelope = SignalProtoMapper.toProto(signal);

    SignalEnvelope parsed = SignalEnvelope.parseFrom(envelope.toByteArray());
    SignalDTO restored = SignalProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(signal);
  }

  private static Stream<Arguments> signalCases() {
    UUID processInstanceId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    ProcessDefinitionKey processDefinitionKey = new ProcessDefinitionKey("signal-process", 3);

    return Stream.of(
        Arguments.of("signal", new SignalDTO("order-cancelled")),
        Arguments.of(
            "newInstanceSubscription",
            new NewInstanceSignalSubscriptionDTO(
                processInstanceId, List.of(10L, 20L, 30L), "order-cancelled")),
        Arguments.of(
            "cancelInstanceSubscription",
            new CancelInstanceSignalSubscriptionDTO(
                processInstanceId, List.of(10L, 20L, 30L), "order-cancelled")),
        Arguments.of(
            "newDefinitionSubscription",
            new NewDefinitionSignalSubscriptionDTO(
                processDefinitionKey, "SignalStartEvent_1", "order-cancelled")),
        Arguments.of(
            "cancelDefinitionSubscription",
            new CancelDefinitionSignalSubscriptionDTO(
                processDefinitionKey, "SignalStartEvent_1", "order-cancelled")));
  }
}
