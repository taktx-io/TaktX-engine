/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

/** Shared TTL / purge helpers for durable dedup state stores. */
public final class DedupStoreSupport {

  private DedupStoreSupport() {}

  /**
   * Purges all store entries whose effective first-seen timestamp is older than {@code
   * retentionMs}.
   *
   * <p>{@link Math#abs(long)} is applied to the stored value so callers can reuse the helper for
   * stores that encode internal sentinels via sign (for example replay protection's negative
   * "already emitted DLQ" marker) as well as for future stores that use plain positive timestamps
   * only.
   */
  public static void purgeExpiredEntries(
      KeyValueStore<String, Long> store, long timestamp, long retentionMs) {
    List<String> expiredKeys = new ArrayList<>();
    try (KeyValueIterator<String, Long> entries = store.all()) {
      while (entries.hasNext()) {
        org.apache.kafka.streams.KeyValue<String, Long> entry = entries.next();
        if (entry.value != null && timestamp - Math.abs(entry.value) >= retentionMs) {
          expiredKeys.add(entry.key);
        }
      }
    }
    expiredKeys.forEach(store::delete);
  }
}
