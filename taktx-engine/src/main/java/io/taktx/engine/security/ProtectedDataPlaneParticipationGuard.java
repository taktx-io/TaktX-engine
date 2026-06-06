/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

/**
 * Decides whether the local engine may participate in protected data-plane processing.
 *
 * <p>In OPEN mode: always permitted. In ANCHORED mode: permitted once the engine's own signing key
 * has been published to {@code taktx-signing-keys} (transient window only — fail-fast startup
 * guarantees the identity material exists before any processing begins).
 */
public class ProtectedDataPlaneParticipationGuard {

  public static final String ENGINE_SIGNING_UNAVAILABLE =
      EngineSecurityReadinessEvaluator.ENGINE_SIGNING_UNAVAILABLE;

  private final boolean anchored;
  private final MessageSigningService messageSigningService;

  public ProtectedDataPlaneParticipationGuard(
      boolean anchored, MessageSigningService messageSigningService) {
    this.anchored = anchored;
    this.messageSigningService = messageSigningService;
  }

  public Decision evaluate() {
    if (!anchored) {
      return Decision.permit();
    }
    if (messageSigningService.isPublicKeyPublished()) {
      return Decision.permit();
    }
    return Decision.blocked(
        ENGINE_SIGNING_UNAVAILABLE,
        "Protected data-plane participation is blocked: anchored mode active but engine signing"
            + " key has not been published yet");
  }

  public record Decision(boolean permitted, String reasonHint, String reasonText) {

    public static Decision permit() {
      return new Decision(true, null, null);
    }

    public static Decision blocked(String reasonHint, String reasonText) {
      return new Decision(false, reasonHint, reasonText);
    }
  }
}
