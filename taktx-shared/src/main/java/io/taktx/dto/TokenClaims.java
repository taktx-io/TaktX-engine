/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Parsed and validated claims from a Platform Service RS256 JWT. Not serialised to Kafka — purely
 * in-memory after validation.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class TokenClaims {
  /** JWT {@code sub} — the authenticated user ID. */
  private String userId;

  /** JWT {@code iss} — the issuing service identifier. */
  private String issuer;

  /** {@code "START"} or {@code "CANCEL"}. */
  private String action;

  /** Process definition ID the token authorises. */
  private String processDefinitionId;

  /** Process definition version the token authorises; -1 means latest. */
  private int version;

  /** Namespace the token is scoped to. */
  private UUID namespaceId;

  /** Unique per-user-action UUID — used for replay prevention. */
  private String auditId;

  private Instant issuedAt;
  private Instant expiresAt;
}
