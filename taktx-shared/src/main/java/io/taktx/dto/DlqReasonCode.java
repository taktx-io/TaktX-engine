/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DlqReasonCode {
  CBOR_DECODE_ERROR(DlqSeverity.MEDIUM),
  CBOR_TYPE_MISMATCH(DlqSeverity.MEDIUM),
  SIGNATURE_MISSING(DlqSeverity.HIGH),
  SIGNATURE_MALFORMED(DlqSeverity.HIGH),
  SIGNATURE_KEY_UNKNOWN(DlqSeverity.HIGH),
  SIGNATURE_KEY_REVOKED(DlqSeverity.HIGH),
  SIGNATURE_VERIFICATION_FAILED(DlqSeverity.HIGH),
  JWT_MISSING(DlqSeverity.MEDIUM),
  JWT_MALFORMED(DlqSeverity.MEDIUM),
  JWT_SIGNATURE_INVALID(DlqSeverity.HIGH),
  AUTHORIZATION_FAILED(DlqSeverity.MEDIUM),
  INSUFFICIENT_ROLE(DlqSeverity.MEDIUM),
  INSUFFICIENT_SCOPE(DlqSeverity.MEDIUM),
  REPLAY_DETECTED(DlqSeverity.CRITICAL),
  PROCESSOR_EXCEPTION(DlqSeverity.MEDIUM),
  TOPIC_NOT_ALLOWED(DlqSeverity.MEDIUM),
  UNKNOWN_REJECTION_REASON(DlqSeverity.LOW);

  private final DlqSeverity severity;
}

