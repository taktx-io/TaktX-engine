package com.flomaestro.takt.dto.v_1_0_0;

/**
 * INITIAL: Initial state, not doing anything, cannot be triggered in any way INITIAL -> ACTIVE:
 * StartEvent timer is started ACTIVE: Active, can be triggered and StartEvent schedules are running
 * ACTIVE -> INACTIVE: Schedules are cancelled. INACTIVE: No new instances will be started.
 */
public enum ProcessDefinitionStateEnum {
  ACTIVE, // Active, can be triggered and StartEvent timers are running
  INACTIVE, // No new instances will be started.
}
