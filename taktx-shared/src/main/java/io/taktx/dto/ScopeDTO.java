/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class ScopeDTO {

  private int subProcessLevel;
  private int activeCnt;
  private ExecutionState state;
  private long elementInstanceCnt;
  private Map<String, Long> gatewayInstances;
  private SubscriptionsDTO subscriptions;

  public ScopeDTO(
      ExecutionState state,
      int activeCnt,
      int subProcessLevel,
      long elementInstanceCnt,
      Map<String, Long> gatewayInstances,
      SubscriptionsDTO subscriptions) {
    this.state = state;
    this.activeCnt = activeCnt;
    this.subProcessLevel = subProcessLevel;
    this.elementInstanceCnt = elementInstanceCnt;
    this.gatewayInstances = gatewayInstances;
    this.subscriptions = subscriptions;
  }
}
