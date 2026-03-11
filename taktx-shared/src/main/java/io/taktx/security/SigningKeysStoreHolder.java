/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

/**
 * Thread-local / process-wide holder for the {@link SigningKeysStore} instance.
 *
 * <p>Kafka deserializers are instantiated reflectively by the consumer, making it impossible to
 * inject dependencies via constructor. This holder lets application code register the store once at
 * startup, and all {@code JsonDeserializer} subclasses pick it up in their {@code configure()}
 * call.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // In TaktXClient.start():
 * SigningKeysStoreHolder.set(myStore);
 *
 * // In JsonDeserializer.configure():
 * SigningKeysStore store = SigningKeysStoreHolder.get();
 * if (store != null) setSigningKeysStore(store);
 * }</pre>
 *
 * <p>Only one store per JVM process is supported — this matches the single-namespace, single-engine
 * model. If multiple namespaces are needed in the future, this can be keyed by namespace.
 */
public final class SigningKeysStoreHolder {

  private static volatile SigningKeysStore instance;

  private SigningKeysStoreHolder() {}

  /** Registers the store. Call this before creating any Kafka consumers. */
  public static void set(SigningKeysStore store) {
    instance = store;
  }

  /** Returns the registered store, or {@code null} if none has been set. */
  public static SigningKeysStore get() {
    return instance;
  }

  /** Clears the stored instance (useful for tests to avoid bleed-over). */
  public static void clear() {
    instance = null;
  }
}
