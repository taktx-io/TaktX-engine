package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * INITIAL: Initial state, not doing anything, cannot be triggered in any way INITIAL -> ACTIVE:
 * StartEvent timer is started ACTIVE: Active, can be triggered and StartEvent schedules are running
 * ACTIVE -> INACTIVE: Schedules are cancelled. INACTIVE: No new instances will be started.
 */
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
