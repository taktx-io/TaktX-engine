/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd.model;

import io.taktx.engine.pi.model.EventBasedGatewayInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class EventBasedGateway extends Gateway {

  @Override
  protected EventBasedGatewayInstance newSpecificGatewayInstance(
      IFlowNodeInstance parentInstance, long elementInstanceId) {
    return new EventBasedGatewayInstance(parentInstance, this, elementInstanceId);
  }
}
