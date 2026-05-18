/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.CorrelationMessageEventTriggerDTO;
import io.taktx.dto.VariablesDTO;
import io.taktx.proto.MessageEventEnvelope;
import org.junit.jupiter.api.Test;

class MessageEventSerializerTest {

  @Test
  void serialize_writesMessageEventEnvelopeBytes() throws Exception {
    try (MessageEventSerializer serializer = new MessageEventSerializer()) {
      byte[] bytes =
          serializer.serialize(
              "message-topic",
              new CorrelationMessageEventTriggerDTO(
                  "payment.received", "order-42", VariablesDTO.of("approved", true)));

      MessageEventEnvelope envelope = MessageEventEnvelope.parseFrom(bytes);

      assertThat(envelope.getCorrTrigger().getMessageName()).isEqualTo("payment.received");
      assertThat(envelope.getCorrTrigger().getCorrelationKey()).isEqualTo("order-42");
      assertThat(envelope.getCorrTrigger().getVariables().getEntriesMap()).containsKey("approved");
    }
  }
}
