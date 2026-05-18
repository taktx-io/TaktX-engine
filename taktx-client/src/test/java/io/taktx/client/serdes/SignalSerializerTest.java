/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NewInstanceSignalSubscriptionDTO;
import io.taktx.proto.SignalEnvelope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignalSerializerTest {

  @Test
  void serialize_writesSignalEnvelopeBytes() throws Exception {
    try (SignalSerializer serializer = new SignalSerializer()) {
      byte[] bytes =
          serializer.serialize(
              "signal-topic",
              new NewInstanceSignalSubscriptionDTO(
                  UUID.fromString("55555555-5555-5555-5555-555555555555"),
                  List.of(1L, 2L),
                  "order-cancelled"));

      SignalEnvelope envelope = SignalEnvelope.parseFrom(bytes);

      assertThat(envelope.getNewInstanceSub().getSignalName()).isEqualTo("order-cancelled");
      assertThat(envelope.getNewInstanceSub().getElementInstanceIdPathList())
          .containsExactly(1L, 2L);
    }
  }
}
