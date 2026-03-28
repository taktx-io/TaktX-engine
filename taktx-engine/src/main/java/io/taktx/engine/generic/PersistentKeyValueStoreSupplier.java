/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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
