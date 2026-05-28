/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

/**
 * Operator-facing namespace security posture.
 *
 * <p>These modes mirror the proposed namespace security-policy model while remaining independent of
 * the current {@link GlobalConfigurationDTO} rollout path.
 */
public enum SecurityMode {
  OPEN,
  SECURED,
  ANCHORED_SECURED,
  MISCONFIGURED_SECURITY
}
