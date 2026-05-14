/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;

class DedupStoreSupportTest {

  @SuppressWarnings("unchecked")
  @Test
  void purgeExpiredEntries_deletesOnlyExpiredKeys_andHonorsNegativeSentinelValues() {
    KeyValueStore<String, Long> store = mock(KeyValueStore.class);
    KeyValueIterator<String, Long> iterator = mock(KeyValueIterator.class);
    List<KeyValue<String, Long>> entries =
        List.of(
            KeyValue.pair("expired-positive", 1_000L),
            KeyValue.pair("expired-negative-sentinel", -1_500L),
            KeyValue.pair("fresh", 4_500L),
            KeyValue.pair("null-value", null));

    when(store.all()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(true, true, true, true, false);
    when(iterator.next())
        .thenReturn(entries.get(0), entries.get(1), entries.get(2), entries.get(3));

    DedupStoreSupport.purgeExpiredEntries(store, 6_000L, 4_000L);

    verify(store).delete("expired-positive");
    verify(store).delete("expired-negative-sentinel");
    verify(store, never()).delete("fresh");
    verify(store, never()).delete("null-value");
    verify(iterator).close();
  }
}

