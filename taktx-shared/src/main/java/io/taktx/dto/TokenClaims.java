/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
