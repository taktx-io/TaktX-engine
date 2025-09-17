/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * INITIAL: Initial state, not doing anything, cannot be triggered in any way INITIAL -> ACTIVE:
 * StartEvent timer is started ACTIVE: Active, can be triggered and StartEvent schedules are running
 * ACTIVE -> INACTIVE: Schedules are cancelled. INACTIVE: No new instances will be started.
 */
@RegisterForReflection
public enum ProcessDefinitionStateEnum {
  ACTIVE("A"), // Active, can be triggered and StartEvent timers are running
  INACTIVE("I"),
  ; // No new instances will be started.

  private final String code;

  ProcessDefinitionStateEnum(String code) {
    this.code = code;
  }

  @JsonValue
  public String getCode() {
    return code;
  }
}
