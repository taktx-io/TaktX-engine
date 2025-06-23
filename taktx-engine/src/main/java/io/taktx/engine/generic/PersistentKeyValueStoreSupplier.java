/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.generic;

import static org.apache.kafka.streams.state.Stores.persistentKeyValueStore;

import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pd.Stores;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;

@ApplicationScoped
@Persistent
public class PersistentKeyValueStoreSupplier implements KeyValueStoreSupplier {

  @Inject TaktConfiguration taktConfiguration;

  @Override
  public KeyValueBytesStoreSupplier get(Stores store) {
    return persistentKeyValueStore(taktConfiguration.getPrefixed(store.getStorename()));
  }
}
