/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
