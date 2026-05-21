/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.CancelCorrelationMessageSubscriptionDTO;
import io.taktx.dto.CancelDefinitionMessageSubscriptionDTO;
import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageEventTriggerDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.MessageEventDTO;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.MessageEventEnvelope;
import io.taktx.proto.MessageEventKeyMessage;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageEventProtoMapperTest {

  @ParameterizedTest(name = "{0} round-trips through MessageEventEnvelope")
  @MethodSource("messageEventCases")
  void messageEventFamily_roundTripsThroughProtoEnvelope(String name, MessageEventDTO event)
      throws Exception {
    MessageEventEnvelope envelope = MessageEventProtoMapper.toProto(event);

    MessageEventEnvelope parsed = MessageEventEnvelope.parseFrom(envelope.toByteArray());
    MessageEventDTO restored = MessageEventProtoMapper.toDto(parsed);

    assertThat(restored).as(name).usingRecursiveComparison().isEqualTo(event);
  }

  @Test
  void messageEventKey_roundTripsThroughProtoMessage() throws Exception {
    MessageEventKeyDTO key = new MessageEventKeyDTO("payment.received");

    MessageEventKeyMessage parsed =
        MessageEventKeyMessage.parseFrom(MessageEventProtoMapper.toProto(key).toByteArray());

    assertThat(MessageEventProtoMapper.toDto(parsed)).usingRecursiveComparison().isEqualTo(key);
  }

  private static Stream<Arguments> messageEventCases() {
    UUID processInstanceId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    return Stream.of(
        Arguments.of(
            "definitionSubscription",
            new DefinitionMessageSubscriptionDTO(
                new ProcessDefinitionKey("payment-process", 4),
                "StartEvent_Message",
                "payment.received")),
        Arguments.of(
            "cancelDefinitionSubscription",
            new CancelDefinitionMessageSubscriptionDTO("payment.received")),
        Arguments.of(
            "correlationSubscription",
            new CorrelationMessageSubscriptionDTO(
                processInstanceId,
                "order-42",
                List.of(10L, 20L, 30L),
                "ReceiveTask_1",
                "payment.received")),
        Arguments.of(
            "cancelCorrelationSubscription",
            new CancelCorrelationMessageSubscriptionDTO("payment.received", "order-42")),
        Arguments.of(
            "definitionTrigger",
            new DefinitionMessageEventTriggerDTO(
                "payment.received", VariablesDTO.of("source", "start", "attempt", 1L))),
        Arguments.of(
            "correlationTrigger",
            new CorrelationMessageEventTriggerDTO(
                "payment.received", "order-42", VariablesDTO.of("amount", 99L, "approved", true))));
  }
}
