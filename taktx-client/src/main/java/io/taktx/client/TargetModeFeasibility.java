/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import io.taktx.dto.SecurityMode;
import java.util.List;

/** Advisory feasibility assessment for a target namespace security posture. */
public record TargetModeFeasibility(
    SecurityMode targetMode,
    TargetModeFeasibilityStatus status,
    boolean feasibleNow,
    List<BlockingIssue> blockers) {

  public TargetModeFeasibility {
    blockers = blockers == null ? List.of() : List.copyOf(blockers);
    if (status == null) {
      status = blockers.isEmpty() ? TargetModeFeasibilityStatus.FEASIBLE : TargetModeFeasibilityStatus.BLOCKED;
    }
  }

  public static TargetModeFeasibility feasible(SecurityMode targetMode) {
    return new TargetModeFeasibility(
        targetMode, TargetModeFeasibilityStatus.FEASIBLE, true, List.of());
  }

  public static TargetModeFeasibility blocked(SecurityMode targetMode, List<BlockingIssue> blockers) {
    return new TargetModeFeasibility(
        targetMode, TargetModeFeasibilityStatus.BLOCKED, false, blockers);
  }
}

