/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
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
    implements WithIoMapping, WithEscalationEventDefinitions, WithErrorEventDefinitions {
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

  private <T> Optional<T> getDefinition(Class<T> clazz) {
    return eventDefinitions.stream().filter(clazz::isInstance).map(clazz::cast).findFirst();
  }
}
