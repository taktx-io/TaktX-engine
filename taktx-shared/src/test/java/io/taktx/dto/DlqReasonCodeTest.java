/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DlqReasonCodeTest {

  @Test
  void replayDetected_isCritical() {
    assertThat(DlqReasonCode.REPLAY_DETECTED.getSeverity()).isEqualTo(DlqSeverity.CRITICAL);
  }

  @Test
  void signatureFailures_areHighSeverity() {
    assertThat(DlqReasonCode.SIGNATURE_MISSING.getSeverity()).isEqualTo(DlqSeverity.HIGH);
    assertThat(DlqReasonCode.SIGNATURE_VERIFICATION_FAILED.getSeverity())
        .isEqualTo(DlqSeverity.HIGH);
  }

  @Test
  void unknownReason_defaultsToLow() {
    assertThat(DlqReasonCode.UNKNOWN_REJECTION_REASON.getSeverity()).isEqualTo(DlqSeverity.LOW);
  }
}
