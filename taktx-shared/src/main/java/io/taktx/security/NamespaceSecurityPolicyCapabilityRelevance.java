/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import java.util.EnumSet;
import java.util.Set;

/** Shared mapping of capability-relevant namespace policy elements for readiness and gating. */
public final class NamespaceSecurityPolicyCapabilityRelevance {

  private NamespaceSecurityPolicyCapabilityRelevance() {}

  public static Set<CapabilityRelevantPolicyElement> relevantElements(
      Set<ParticipantCapability> ignoredCapabilities) {
    return Set.copyOf(EnumSet.of(CapabilityRelevantPolicyElement.MODE));
  }

  /** Returns the normalized authoritative policy relevant to the supplied capabilities. */
  public static NamespaceSecurityPolicyDTO relevantPolicyForCapabilities(
      Set<ParticipantCapability> ignoredCapabilities, NamespaceSecurityPolicyDTO policy) {
    if (policy == null) {
      return null;
    }
    return NamespaceSecurityPolicySupport.normalize(policy);
  }
}
