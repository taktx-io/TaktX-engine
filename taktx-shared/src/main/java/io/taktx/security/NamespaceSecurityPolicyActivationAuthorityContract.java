/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.SecurityActivationState;
import java.util.EnumSet;
import java.util.Set;

/** Shared first-slice activation authority contract for namespace security policy lifecycle changes. */
public final class NamespaceSecurityPolicyActivationAuthorityContract {

  private NamespaceSecurityPolicyActivationAuthorityContract() {}

  /** Returns the sole activation authority for the first slice. */
  public static NamespaceSecurityPolicyActivationAuthority soleActivationAuthority() {
    return NamespaceSecurityPolicyActivationAuthority.PLATFORM_SERVICE;
  }

  /** Returns participants that may report readiness but are not allowed to authoritatively activate. */
  public static Set<NamespaceSecurityPolicyActivationAuthority> nonAuthoritativeParticipants() {
    return Set.copyOf(
        EnumSet.of(
            NamespaceSecurityPolicyActivationAuthority.PARTICIPANT_RUNTIME,
            NamespaceSecurityPolicyActivationAuthority.UNKNOWN));
  }

  /**
   * Returns whether the supplied authority may perform the requested activation-state transition.
   *
   * <p>In the first slice, only {@code PLATFORM_SERVICE} may transition a policy into {@code ACTIVE}.
   * Non-authoritative participants may report posture but must not individually decide activation.
   */
  public static boolean mayTransitionActivationState(
      NamespaceSecurityPolicyActivationAuthority authority,
      SecurityActivationState from,
      SecurityActivationState to) {
    if (to == null) {
      return false;
    }
    if (to != SecurityActivationState.ACTIVE) {
      return true;
    }
    return authority == soleActivationAuthority() && from != SecurityActivationState.ACTIVE;
  }
}

