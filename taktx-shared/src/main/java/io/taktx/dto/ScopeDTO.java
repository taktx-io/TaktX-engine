/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import java.util.Set;
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
  private ScopeState state;

  private int activeCnt;

  private long elementInstanceCnt;

  private Map<String, Long> gatewayInstances;

  private Map<String, Set<String>> messageSubscriptions;

  private Set<InstanceScheduleKeyDTO> scheduleKeys;

  public ScopeDTO(
      ScopeState state,
      int activeCnt,
      long elementInstanceCnt,
      Map<String, Long> gatewayInstances,
      Map<String, Set<String>> messageSubscriptions,
      Set<InstanceScheduleKeyDTO> scheduleKeys) {
    this.state = state;
    this.activeCnt = activeCnt;
    this.elementInstanceCnt = elementInstanceCnt;
    this.gatewayInstances = gatewayInstances;
    this.messageSubscriptions = messageSubscriptions;
    this.scheduleKeys = scheduleKeys;
  }
}
