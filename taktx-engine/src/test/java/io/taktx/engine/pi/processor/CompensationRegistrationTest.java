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

  // ── Req §9: repeated activity completions ────────────────────────────────

  @Test
  void findRegistrationsForThrow_returnsAllRepeatCompletions() {
    // Same activity completes 3 times (e.g. loop) → 3 separate registrations
    scope.addCompensationRegistration(reg("r1", "task-a", false));
    scope.addCompensationRegistration(reg("r2", "task-a", false));
    scope.addCompensationRegistration(reg("r3", "task-a", false));

    List<CompensationRegistration> result = scope.findRegistrationsForThrow("task-a");

    assertThat(result)
        .hasSize(3)
        .extracting(CompensationRegistration::getRegistrationKey)
        .containsExactlyInAnyOrder("r1", "r2", "r3");
  }

  @Test
  void findRegistrationsForThrow_onlyConsumedRepeatIsExcluded() {
    // First completion already compensated, second still available
    scope.addCompensationRegistration(reg("r1", "task-a", true)); // consumed
    scope.addCompensationRegistration(reg("r2", "task-a", false)); // not yet consumed

    List<CompensationRegistration> result = scope.findRegistrationsForThrow("task-a");

    assertThat(result)
        .hasSize(1)
        .extracting(CompensationRegistration::getRegistrationKey)
        .containsOnly("r2");
  }

  // ── Req §10: multi-instance treated as one unit ───────────────────────────

  @Test
  void findRegistrationsForThrow_multipleHandlersPendingAllMustComplete() {
    // When multiple handlers are dispatched for repeated completions, each
    // gets its own pending key — all must complete before the throw event unblocks
    CompensationTriggerState triggerState = new CompensationTriggerState(99L, "task-a");
    triggerState.addPendingHandler(10L); // handler for 1st completion
    triggerState.addPendingHandler(11L); // handler for 2nd completion
    triggerState.addPendingHandler(12L); // handler for 3rd completion
    scope.addCompensationTriggerState(triggerState);

    // First two complete — still pending
    assertThat(scope.markHandlerCompleted(10L)).isEmpty();
    assertThat(scope.markHandlerCompleted(11L)).isEmpty();
    // Last one completes → throw event unblocked
    assertThat(scope.markHandlerCompleted(12L)).isPresent();
    assertThat(triggerState.getPendingHandlerInstanceKeys()).isEmpty();
    assertThat(triggerState.getCompletedHandlerInstanceKeys())
        .containsExactlyInAnyOrder(10L, 11L, 12L);
  }

  // ── Req §5: scope-level isolation ────────────────────────────────────────

  @Test
  void findRegistrationsForThrow_nullActivityIdFindsAllUnconsumedIncludingRepeats() {
    // No activityRef → all unconsumed registrations across all activities
    scope.addCompensationRegistration(reg("r1", "task-a", false));
    scope.addCompensationRegistration(reg("r2", "task-a", false)); // second completion of task-a
    scope.addCompensationRegistration(reg("r3", "task-b", false));
    scope.addCompensationRegistration(reg("r4", "task-b", true)); // consumed

    List<CompensationRegistration> result = scope.findRegistrationsForThrow(null);

    assertThat(result)
        .hasSize(3)
        .extracting(CompensationRegistration::getRegistrationKey)
        .containsExactlyInAnyOrder("r1", "r2", "r3");
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
