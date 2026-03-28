/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.ErrorEventSignal;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ErrorEventDefinition extends EventDefinition {
  private String errorRef;

  @Setter private ErrorEvent referencedError;

  public boolean handlesEvent(EventSignal event) {
    if (event instanceof ErrorEventSignal errorEventSignal) {
      return referencedError != null && referencedError.code().equals(errorEventSignal.getCode());
    }
    return false;
  }

  public boolean handlesEventCatchAll(EventSignal event) {
    if (event instanceof ErrorEventSignal) {
      return referencedError == null;
    }
    return false;
  }
}
