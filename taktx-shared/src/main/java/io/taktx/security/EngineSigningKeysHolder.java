/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.security;

/**
 * Process-wide holder for an Ed25519 public-key resolver function, used by {@link
 * io.taktx.serdes.JsonDeserializer} when running inside the engine.
 *
 * <p>On the engine side the signing keys live in a Kafka Streams {@code GlobalKTable} rather than a
 * standalone {@link SigningKeysStore} consumer. This holder lets the engine register a thin lambda
 * that delegates to the KTable, so {@code JsonDeserializer} can verify Ed25519 signatures on
 * incoming {@code process-instance-trigger} records without a separate Kafka consumer and without
 * re-serialising the payload in the processor.
 *
 * <p>Takes priority over {@link SigningKeysStoreHolder} when both are set — the engine always uses
 * its own KTable which is already kept up-to-date by Kafka Streams.
 */
public final class EngineSigningKeysHolder {

  @FunctionalInterface
  public interface KeyResolver {
    /**
     * Returns the base64-encoded Ed25519 public key for the given keyId, or {@code null} if the key
     * is unknown or revoked.
     */
    String resolvePublicKey(String keyId);
  }

  private static volatile KeyResolver instance;

  private EngineSigningKeysHolder() {}

  public static void set(KeyResolver resolver) {
    instance = resolver;
  }

  public static KeyResolver get() {
    return instance;
  }

  public static void clear() {
    instance = null;
  }
}
