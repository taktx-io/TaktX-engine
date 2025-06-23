/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pd.model;

import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class ThrowEvent extends Event
    implements WithEscalationEventDefinitions, WithErrorEventDefinitions {
  private Set<EventDefinition> eventDefinitions;

  public Optional<LinkEventDefinition> getLinkventDefinition() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition.class::isInstance)
        .map(LinkEventDefinition.class::cast)
        .findFirst();
  }

  public Optional<EscalationEventDefinition> getEscalationEventDefinition() {
    return eventDefinitions.stream()
        .filter(EscalationEventDefinition.class::isInstance)
        .map(EscalationEventDefinition.class::cast)
        .findFirst();
  }

  public Optional<ErrorEventDefinition> getErrorEventDefinition() {
    return eventDefinitions.stream()
        .filter(ErrorEventDefinition.class::isInstance)
        .map(ErrorEventDefinition.class::cast)
        .findFirst();
  }
}
