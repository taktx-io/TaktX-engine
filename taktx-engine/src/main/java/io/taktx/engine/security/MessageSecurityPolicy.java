/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.KeyRole;
import java.util.Set;

/** Declarative security requirements for a protected (topic, DTO/message) combination. */
public record MessageSecurityPolicy(
    String topicName,
    Class<?> messageClass,
    Set<KeyRole> allowedRoles,
    boolean requireSignature,
    boolean requireReplay,
    boolean requireJwt,
    boolean allowEngineSignatureAsJwtEquivalent) {

  public MessageSecurityPolicy {
    allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
  }

  /**
   * Returns the minimum trusted role required for a signed message.
   *
   * <p>The current trust hierarchy is {@code PLATFORM ⊇ ENGINE ⊇ CLIENT}, so protected message
   * types in this first registry slice are expressed as contiguous allowlists rooted at a minimum
   * role.
   */
  public KeyRole minimumAllowedRole() {
    if (allowedRoles.contains(KeyRole.CLIENT)) {
      return KeyRole.CLIENT;
    }
    if (allowedRoles.contains(KeyRole.ENGINE)) {
      return KeyRole.ENGINE;
    }
    if (allowedRoles.contains(KeyRole.PLATFORM)) {
      return KeyRole.PLATFORM;
    }
    return null;
  }

  public static Builder builder(String topicName, Class<?> messageClass) {
    return new Builder(topicName, messageClass);
  }

  public static final class Builder {
    private final String topicName;
    private final Class<?> messageClass;
    private Set<KeyRole> allowedRoles = Set.of();
    private boolean requireSignature;
    private boolean requireReplay;
    private boolean requireJwt;
    private boolean allowEngineSignatureAsJwtEquivalent;

    private Builder(String topicName, Class<?> messageClass) {
      this.topicName = topicName;
      this.messageClass = messageClass;
    }

    public Builder allowedRoles(Set<KeyRole> allowedRoles) {
      this.allowedRoles = allowedRoles;
      return this;
    }

    public Builder requireSignature(boolean requireSignature) {
      this.requireSignature = requireSignature;
      return this;
    }

    public Builder requireReplay(boolean requireReplay) {
      this.requireReplay = requireReplay;
      return this;
    }

    public Builder requireJwt(boolean requireJwt) {
      this.requireJwt = requireJwt;
      return this;
    }

    public Builder allowEngineSignatureAsJwtEquivalent(
        boolean allowEngineSignatureAsJwtEquivalent) {
      this.allowEngineSignatureAsJwtEquivalent = allowEngineSignatureAsJwtEquivalent;
      return this;
    }

    public MessageSecurityPolicy build() {
      return new MessageSecurityPolicy(
          topicName,
          messageClass,
          allowedRoles,
          requireSignature,
          requireReplay,
          requireJwt,
          allowEngineSignatureAsJwtEquivalent);
    }
  }
}
