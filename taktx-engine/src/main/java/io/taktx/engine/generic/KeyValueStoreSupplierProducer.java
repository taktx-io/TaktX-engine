/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
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
