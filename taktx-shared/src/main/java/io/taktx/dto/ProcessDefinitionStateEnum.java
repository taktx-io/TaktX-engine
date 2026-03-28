/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
