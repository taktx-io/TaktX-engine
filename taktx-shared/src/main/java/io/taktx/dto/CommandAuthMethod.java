/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum CommandAuthMethod {
  /** JWT-only authorization — X-TaktX-Authorization verified, no Ed25519 header present. */
  JWT,
  /** Ed25519-only signing — X-TaktX-Signature verified, no JWT header present. */
  ED25519,
  /**
   * Both JWT authorization and Ed25519 signing were verified on the same command.
   *
   * <p>The JWT provides the identity/authorization context ({@code userId}, {@code issuer}); the
   * Ed25519 signature provides message authenticity ({@code signerKeyId}, {@code signerOwner}).
   * Both must have passed for this value to be set.
   */
  JWT_AND_ED25519,
  NONE
}
