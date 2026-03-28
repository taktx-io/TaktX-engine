/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.generic;

import io.taktx.engine.config.TaktConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeyValueStoreSupplierProducer {

  @Inject TaktConfiguration config;

  @Inject @InMemory InMemoryKeyValueStoreSupplier inMemoryKeyValueStoreSupplier;

  @Inject @Persistent PersistentKeyValueStoreSupplier persistentKeyValueStoreSupplier;

  @Produces
  public KeyValueStoreSupplier produceKeyValueStoreSupplier() {
    String supplierType = config.getSupplierType();
    if ("persistent".equalsIgnoreCase(supplierType)) {
      return persistentKeyValueStoreSupplier;
    } else if ("inmemory".equalsIgnoreCase(supplierType)) {
      return inMemoryKeyValueStoreSupplier;
    } else {
      throw new IllegalArgumentException(
          "Unknown key value store type: "
              + supplierType
              + ". Supported types are 'persistent' and 'inmemory'");
    }
  }
}
