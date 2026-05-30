/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

/** Shared machine-readable issue codes used by posture, readiness, and mutation helpers. */
public final class SecurityPostureIssueCodes {

  public static final String ACTIVATION_TIMEOUT = "ACTIVATION_TIMEOUT";
  public static final String POLICY_REJECTION = "POLICY_REJECTION";
  public static final String READINESS_MISMATCH = "READINESS_MISMATCH";
  public static final String BREAK_GLASS_DOWNGRADE = "BREAK_GLASS_DOWNGRADE";
  public static final String BREAK_GLASS_DOWNGRADE_REJECTED = "BREAK_GLASS_DOWNGRADE_REJECTED";
  public static final String INVALID_POLICY_MUTATION = "INVALID_POLICY_MUTATION";
  public static final String TRUST_ANCHOR_MISSING = "TRUST_ANCHOR_MISSING";
  public static final String PARTICIPANT_STATUS_STALE = "PARTICIPANT_STATUS_STALE";
  public static final String PARTICIPANT_NOT_READY = "PARTICIPANT_NOT_READY";
  public static final String AUTHORITATIVE_WRITER_UNCONFIGURED =
      "AUTHORITATIVE_WRITER_UNCONFIGURED";
  public static final String AUTHORITATIVE_WRITER_UNAVAILABLE = "AUTHORITATIVE_WRITER_UNAVAILABLE";
  public static final String TARGET_MODE_UNSUPPORTED = "TARGET_MODE_UNSUPPORTED";

  private SecurityPostureIssueCodes() {}
}
