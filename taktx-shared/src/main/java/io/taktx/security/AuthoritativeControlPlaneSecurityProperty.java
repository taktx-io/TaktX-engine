/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/** Security properties expected of authoritative control-plane writers. */
public enum AuthoritativeControlPlaneSecurityProperty {
  BROKER_AUTHORIZATION_REQUIRED,
  TRUSTED_WRITER_PATH_ONLY,
  FIXED_RECORD_KEY_REQUIRED,
  INTEGRITY_PROTECTION_REQUIRED_IN_SECURED_MODES,
  BREAK_GLASS_METADATA_REQUIRED_FOR_DOWNGRADE
}
