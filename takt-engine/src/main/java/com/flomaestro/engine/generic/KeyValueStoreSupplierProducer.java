/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.engine.generic;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class KeyValueStoreSupplierProducer {

  @Inject
  @ConfigProperty(name = "takt.engine.keyvaluestoretype")
  String supplierType;

  @Inject @InMemory InMemoryKeyValueStoreSupplier inMemoryKeyValueStoreSupplier;

  @Inject @Persistent PersistentKeyValueStoreSupplier persistentKeyValueStoreSupplier;

  @Produces
  public KeyValueStoreSupplier produceKeyValueStoreSupplier() {
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
