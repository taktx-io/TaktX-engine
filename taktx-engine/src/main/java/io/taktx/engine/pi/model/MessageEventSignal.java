/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.model;

import io.taktx.engine.pd.model.EventSignal;
import io.taktx.proto.VariableValue;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MessageEventSignal extends EventSignal {
  private String name;

  public MessageEventSignal(
      FlowNodeInstance<?> fLowNodeInstance, String name, Map<String, VariableValue> variables) {
    super(fLowNodeInstance, variables);
    this.name = name;
  }
}
