/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
