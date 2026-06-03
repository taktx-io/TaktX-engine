/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.engine.pi.model.CompensationRegistration;
import io.taktx.engine.pi.model.CompensationTriggerState;
import io.taktx.engine.pi.model.Scope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompensationRegistrationTest {

  private Scope scope;

  @BeforeEach
  void setUp() {
    scope = new Scope();
  }

  @Test
  void findRegistrationsForThrow_returnsAllUnconsumedWhenActivityIdIsNull() {
    scope.addCompensationRegistration(reg("r1", "task-a", false));
    scope.addCompensationRegistration(reg("r2", "task-b", false));

    List<CompensationRegistration> result = scope.findRegistrationsForThrow(null);

    assertThat(result).hasSize(2);
  }

  @Test
  void findRegistrationsForThrow_filtersConsumed() {
    scope.addCompensationRegistration(reg("r1", "task-a", false));
    scope.addCompensationRegistration(reg("r2", "task-a", true));

    List<CompensationRegistration> result = scope.findRegistrationsForThrow("task-a");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRegistrationKey()).isEqualTo("r1");
  }

  @Test
  void findRegistrationsForThrow_filtersOnActivityId() {
    scope.addCompensationRegistration(reg("r1", "task-a", false));
    scope.addCompensationRegistration(reg("r2", "task-b", false));

    List<CompensationRegistration> result = scope.findRegistrationsForThrow("task-a");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRegistrationKey()).isEqualTo("r1");
  }

  @Test
  void markHandlerCompleted_returnsEmptyWhenOtherHandlersPending() {
    CompensationTriggerState triggerState = new CompensationTriggerState(99L, null);
    triggerState.addPendingHandler(1L);
    triggerState.addPendingHandler(2L);
    scope.addCompensationTriggerState(triggerState);

    Optional<CompensationTriggerState> result = scope.markHandlerCompleted(1L);

    assertThat(result).isEmpty();
    assertThat(triggerState.getPendingHandlerInstanceKeys()).containsOnly(2L);
    assertThat(triggerState.getCompletedHandlerInstanceKeys()).containsOnly(1L);
  }

  @Test
  void markHandlerCompleted_returnsStateWhenAllHandlersDone() {
    CompensationTriggerState triggerState = new CompensationTriggerState(99L, null);
    triggerState.addPendingHandler(1L);
    scope.addCompensationTriggerState(triggerState);

    Optional<CompensationTriggerState> result = scope.markHandlerCompleted(1L);

    assertThat(result).isPresent();
    assertThat(result.get().getThrowEventInstanceKey()).isEqualTo(99L);
    assertThat(result.get().getPendingHandlerInstanceKeys()).isEmpty();
  }

  @Test
  void markHandlerCompleted_returnsEmptyForUnknownHandler() {
    Optional<CompensationTriggerState> result = scope.markHandlerCompleted(42L);

    assertThat(result).isEmpty();
  }

  @Test
  void findTriggerStateByThrowKey_findsCorrectState() {
    CompensationTriggerState state = new CompensationTriggerState(77L, null);
    scope.addCompensationTriggerState(state);

    Optional<CompensationTriggerState> found = scope.findTriggerStateByThrowKey(77L);
    assertThat(found).isPresent();
    assertThat(found.get().getThrowEventInstanceKey()).isEqualTo(77L);
  }

  @Test
  void findTriggerStateByThrowKey_returnsEmptyForUnknownKey() {
    assertThat(scope.findTriggerStateByThrowKey(999L)).isEmpty();
  }

  private static CompensationRegistration reg(String key, String activityId, boolean consumed) {
    CompensationRegistration reg = new CompensationRegistration();
    reg.setRegistrationKey(key);
    reg.setActivityId(activityId);
    reg.setHandlerId("undo-" + activityId);
    reg.setBoundaryEventId("boundary-" + activityId);
    reg.setConsumed(consumed);
    return reg;
  }
}
