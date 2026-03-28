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
public abstract class CatchEvent extends Event
    implements WithIoMapping,
        WithEscalationEventDefinitions,
        WithErrorEventDefinitions,
        WithSignalEventDefinitions {
  private Set<EventDefinition> eventDefinitions;

  public Optional<TimerEventDefinition> getTimerEventDefinition() {
    return getDefinition(TimerEventDefinition.class);
  }

  public Optional<MessageEventDefinition> getMessageventDefinition() {
    return getDefinition(MessageEventDefinition.class);
  }

  public Optional<LinkEventDefinition> getLinkventDefinition() {
    return getDefinition(LinkEventDefinition.class);
  }

  public Optional<TerminateEventDefinition> getTerminateEventDefinition() {
    return getDefinition(TerminateEventDefinition.class);
  }

  public Optional<EscalationEventDefinition> getEscalationEventDefinition() {
    return getDefinition(EscalationEventDefinition.class);
  }

  public Optional<ErrorEventDefinition> getErrorEventDefinition() {
    return getDefinition(ErrorEventDefinition.class);
  }

  public Optional<SignalEventDefinition> getSignalEventDefinition() {
    return getDefinition(SignalEventDefinition.class);
  }

  private <T> Optional<T> getDefinition(Class<T> clazz) {
    return eventDefinitions.stream().filter(clazz::isInstance).map(clazz::cast).findFirst();
  }
}
