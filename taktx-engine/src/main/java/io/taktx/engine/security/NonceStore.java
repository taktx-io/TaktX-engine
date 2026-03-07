/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.engine.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for seen auditIds, used to prevent command replay attacks.
 *
 * <p>Per-instance: Kafka partition assignment ensures the same process instance UUID always lands
 * on the same engine consumer, so a local nonce store is sufficient.
 */
@ApplicationScoped
public class NonceStore {

  private final Cache<String, Boolean> seen =
      Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(100_000).build();

  /**
   * Records the auditId and returns whether it was new.
   *
   * @return {@code true} if the auditId is new (command should proceed); {@code false} if it was
   *     already seen (replay — reject).
   */
  public boolean checkAndRecord(String auditId) {
    if (auditId == null || auditId.isBlank()) return true; // no nonce — allow
    if (seen.getIfPresent(auditId) != null) return false; // replay detected
    seen.put(auditId, Boolean.TRUE);
    return true;
  }
}
