/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.generic;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.CorrelationMessageSubscriptionDTO;
import io.taktx.dto.DefinitionMessageSubscriptionDTO;
import io.taktx.dto.ProcessDefinitionKey;
import io.taktx.engine.pd.CorrelationMessageSubscriptions;
import io.taktx.engine.pd.DefinitionMessageSubscriptions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalStoreSerdeTest {

  @Test
  void hashVersionMapSerde_roundTrips() {
    HashVersionMapSerde serde = new HashVersionMapSerde();
    Map<String, Integer> original = new LinkedHashMap<>(Map.of("hash-a", 1, "hash-b", 3));

    Map<String, Integer> decoded =
        serde.deserializer().deserialize("topic", serde.serializer().serialize("topic", original));

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void definitionMessageSubscriptionsSerde_roundTrips() {
    DefinitionMessageSubscriptionsSerde serde = new DefinitionMessageSubscriptionsSerde();
    DefinitionMessageSubscriptionDTO subscription =
        new DefinitionMessageSubscriptionDTO(
            new ProcessDefinitionKey("order-process", 7), "message-start", "payment.received");
    DefinitionMessageSubscriptions original =
        new DefinitionMessageSubscriptions(
            new LinkedHashMap<>(Map.of(subscription.toMessageEventKey(), subscription)));

    DefinitionMessageSubscriptions decoded =
        serde.deserializer().deserialize("topic", serde.serializer().serialize("topic", original));

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void correlationMessageSubscriptionsSerde_roundTrips() {
    CorrelationMessageSubscriptionsSerde serde = new CorrelationMessageSubscriptionsSerde();
    CorrelationMessageSubscriptionDTO subscription =
        new CorrelationMessageSubscriptionDTO(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "order-42",
            List.of(10L, 20L),
            "receive-payment",
            "payment.received");
    CorrelationMessageSubscriptions original =
        new CorrelationMessageSubscriptions(
            new LinkedHashMap<>(Map.of(subscription.getCorrelationKey(), subscription)));

    CorrelationMessageSubscriptions decoded =
        serde.deserializer().deserialize("topic", serde.serializer().serialize("topic", original));

    assertThat(decoded).isEqualTo(original);
  }
}
