/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NonceStoreTest {

  @Test
  void firstAuditId_isAccepted() {
    NonceStore store = new NonceStore();
    assertThat(store.checkAndRecord("audit-1")).isTrue();
  }

  @Test
  void sameAuditId_secondTime_isRejected() {
    NonceStore store = new NonceStore();
    store.checkAndRecord("audit-1");
    assertThat(store.checkAndRecord("audit-1")).isFalse();
  }

  @Test
  void differentAuditIds_areAllAccepted() {
    NonceStore store = new NonceStore();
    assertThat(store.checkAndRecord("audit-a")).isTrue();
    assertThat(store.checkAndRecord("audit-b")).isTrue();
    assertThat(store.checkAndRecord("audit-c")).isTrue();
  }

  @Test
  void nullAuditId_isAllowed_withoutRecording() {
    NonceStore store = new NonceStore();
    // null means no nonce — always allow
    assertThat(store.checkAndRecord(null)).isTrue();
    assertThat(store.checkAndRecord(null)).isTrue();
  }

  @Test
  void blankAuditId_isAllowed_withoutRecording() {
    NonceStore store = new NonceStore();
    assertThat(store.checkAndRecord("  ")).isTrue();
    assertThat(store.checkAndRecord("  ")).isTrue();
  }

  @Test
  void multipleDistinctAuditIds_thenReplay_isRejected() {
    NonceStore store = new NonceStore();
    for (int i = 0; i < 100; i++) {
      assertThat(store.checkAndRecord("id-" + i)).isTrue();
    }
    // Re-check the first one — must be rejected
    assertThat(store.checkAndRecord("id-0")).isFalse();
  }
}
