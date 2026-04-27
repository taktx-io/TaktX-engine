/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;

/**
 * Outcome of a successful {@link VerificationCore#verify} call.
 *
 * <p>Encapsulates the resolved signing-key entry, the derived trusted role, and the canonical key
 * ID extracted from the {@code X-TaktX-Signature} header. A context object is only produced when
 * all trust checks pass; failures throw {@link io.taktx.security.AuthorizationTokenException}
 * instead.
 *
 * <p>The {@link #role()} field is derived exclusively from {@link SigningKeyDTO#effectiveRole()} on
 * the key entry returned by the {@code taktx-signing-keys} KTable — it is never sourced from
 * caller-supplied headers or payload fields.
 *
 * <p>The {@link #signatureValid()} sentinel is {@code true} whenever this record is returned (all
 * key-trust checks passed). It is included as a future hook for structured-logging and metrics
 * counters planned under Epic H.
 */
public record VerifiedMessageContext(
    /** Key ID extracted from the {@code X-TaktX-Signature} header (before the first {@code .}). */
    String keyId,
    /** Full signing-key entry resolved from the {@code taktx-signing-keys} KTable. */
    SigningKeyDTO key,
    /**
     * Effective role derived from trusted key metadata — never from headers or payload fields.
     *
     * @see SigningKeyDTO#effectiveRole()
     */
    KeyRole role,
    /**
     * {@code true} when all trust checks (key found, non-revoked, trusted for the required role)
     * passed. Included for use by future structured logging / Epic H metric hooks.
     */
    boolean signatureValid) {}

