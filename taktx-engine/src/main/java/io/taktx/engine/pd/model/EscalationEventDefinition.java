/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.EscalationEventSignal;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class EscalationEventDefinition extends EventDefinition {
  private String escalationRef;

  @Setter private EscalationEvent referencedEscalation;

  public boolean handlesEvent(EventSignal event) {
    if (event instanceof EscalationEventSignal escalationEventSignal) {
      return referencedEscalation != null
          && referencedEscalation.code().equals(escalationEventSignal.getCode());
    }
    return false;
  }

  public boolean handlesEventCatchAll(EventSignal event) {
    if (event instanceof EscalationEventSignal) {
      return referencedEscalation == null;
    }
    return false;
  }
}
