/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/**
 * Process-wide holder for the Ed25519 signing function used by {@link
 * io.taktx.serdes.SigningSerializer}.
 *
 * <p>Registered once at engine startup by {@code MessageSigningService} and at worker startup by
 * {@code TaktXClient}. The {@link io.taktx.serdes.SigningSerializer} picks it up in {@code
 * serialize()} without needing constructor injection — even though Kafka instantiates serializers
 * reflectively.
 *
 * <p>The signing function receives the serialised payload bytes and returns the full {@code
 * X-TaktX-Signature} header value (e.g. {@code "engine-key-1.<base64sig>"}), or {@code null} if
 * signing is disabled or not yet configured.
 */
public final class SigningServiceHolder {

  @FunctionalInterface
  public interface SigningFunction {
    /**
     * @return header value string, or {@code null} to skip signing
     */
    String sign(byte[] payload);
  }

  private static volatile SigningFunction instance;

  private SigningServiceHolder() {}

  public static void set(SigningFunction fn) {
    instance = fn;
  }

  public static SigningFunction get() {
    return instance;
  }

  public static void clear() {
    instance = null;
  }
}
