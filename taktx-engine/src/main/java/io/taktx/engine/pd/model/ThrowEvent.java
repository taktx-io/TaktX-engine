/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
    implements WithEscalationEventDefinitions,
        WithErrorEventDefinitions,
        WithSignalEventDefinitions {
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

  public Optional<SignalEventDefinition> getSignalEventDefinition() {
    return eventDefinitions.stream()
        .filter(SignalEventDefinition.class::isInstance)
        .map(SignalEventDefinition.class::cast)
        .findFirst();
  }

  public Optional<TerminateEventDefinition> getTerminateEventDefinition() {
    return eventDefinitions.stream()
        .filter(TerminateEventDefinition.class::isInstance)
        .map(TerminateEventDefinition.class::cast)
        .findFirst();
  }
}
