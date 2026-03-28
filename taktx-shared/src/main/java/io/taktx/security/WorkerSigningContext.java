/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.security;

/**
 * Holds the Ed25519 signing identity for a worker process.
 *
 * <p>When present, every outgoing Kafka response record is signed with the worker's private key and
 * the {@code X-TaktX-Signature: <keyId>.<base64sig>} header is attached. The engine verifies this
 * header using the public key it looks up by {@code keyId} from the {@code taktx-signing-keys}
 * KTable.
 *
 * <h3>Required configuration for a worker client</h3>
 *
 * <p>Generate the key pair once using openssl and set three environment variables:
 *
 * <pre>{@code
 * # 1. Generate private key
 * openssl genpkey -algorithm Ed25519 -out worker-private.pem
 *
 * # 2. Derive public key (done once at key-generation time, not at runtime)
 * openssl pkey -in worker-private.pem -pubout -out worker-public.pem
 *
 * # 3. Export as base64 DER
 * export TAKTX_SIGNING_PRIVATE_KEY=$(openssl pkey -in worker-private.pem -outform DER | base64)
 * export TAKTX_SIGNING_PUBLIC_KEY=$(openssl pkey -in worker-public.pem -pubin -outform DER | base64)
 * export TAKTX_SIGNING_KEY_ID=my-worker-key-1
 * }</pre>
 *
 * <p>When {@link #fromEnvironment()} is called (by {@link io.taktx.client.TaktXClient} at startup),
 * it reads all three values. {@code TaktXClient.start()} then automatically publishes the public
 * key to the {@code taktx-signing-keys} topic so the engine can verify this worker's responses.
 *
 * <p>The public key may be {@code null} if the caller handles key publication separately (e.g.
 * tests that call {@link SigningKeyRegistrar} directly) — in that case auto-publication is skipped.
 */
public class WorkerSigningContext {

  private static final String ENV_VAR = "TAKTX_SIGNING_PRIVATE_KEY";
  private static final String SYS_PROP = "taktx.signing.private-key";
  private static final String PUBLIC_ENV_VAR = "TAKTX_SIGNING_PUBLIC_KEY";
  private static final String PUBLIC_SYS_PROP = "taktx.signing.public-key";
  private static final String KEY_ID_ENV_VAR = "TAKTX_SIGNING_KEY_ID";
  private static final String KEY_ID_SYS_PROP = "taktx.signing.key-id";

  private final String privateKeyBase64;
  private final String publicKeyBase64;
  private final String keyId;

  private WorkerSigningContext(String privateKeyBase64, String publicKeyBase64, String keyId) {
    this.privateKeyBase64 = privateKeyBase64;
    this.publicKeyBase64 = publicKeyBase64;
    this.keyId = keyId;
  }

  /**
   * Creates a {@code WorkerSigningContext} from explicit values.
   *
   * @param privateKeyBase64 base64-encoded Ed25519 private key (PKCS8 DER)
   * @param publicKeyBase64 base64-encoded Ed25519 public key (X.509 DER), or {@code null} if
   *     auto-publication to the signing-keys topic is not needed
   * @param keyId unique identifier for this key, e.g. {@code "worker-billing-001"}
   */
  public static WorkerSigningContext of(
      String privateKeyBase64, String publicKeyBase64, String keyId) {
    if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
      throw new IllegalArgumentException("privateKeyBase64 must not be blank");
    }
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException("keyId must not be blank");
    }
    return new WorkerSigningContext(privateKeyBase64, publicKeyBase64, keyId);
  }

  /**
   * Convenience factory without a public key — used when the caller handles key publication
   * separately (e.g. tests using {@link SigningKeyRegistrar} directly).
   */
  public static WorkerSigningContext of(String privateKeyBase64, String keyId) {
    return of(privateKeyBase64, null, keyId);
  }

  /**
   * Attempts to load the signing context from environment variables or system properties.
   *
   * <ul>
   *   <li>Private key: {@code TAKTX_SIGNING_PRIVATE_KEY} / {@code taktx.signing.private-key}
   *   <li>Public key: {@code TAKTX_SIGNING_PUBLIC_KEY} / {@code taktx.signing.public-key}
   *   <li>Key ID: {@code TAKTX_SIGNING_KEY_ID} / {@code taktx.signing.key-id}
   * </ul>
   *
   * @return a populated {@code WorkerSigningContext}, or {@code null} if no private key is
   *     configured (signing is disabled)
   * @throws IllegalArgumentException if a private key is set but no key ID is provided
   */
  public static WorkerSigningContext fromEnvironment() {
    String privateKey = System.getenv(ENV_VAR);
    if (privateKey == null || privateKey.isBlank()) privateKey = System.getProperty(SYS_PROP);
    if (privateKey == null || privateKey.isBlank()) return null;

    String publicKey = System.getenv(PUBLIC_ENV_VAR);
    if (publicKey == null || publicKey.isBlank()) publicKey = System.getProperty(PUBLIC_SYS_PROP);

    String keyId = System.getenv(KEY_ID_ENV_VAR);
    if (keyId == null || keyId.isBlank()) keyId = System.getProperty(KEY_ID_SYS_PROP);
    if (keyId == null || keyId.isBlank()) {
      throw new IllegalArgumentException(
          "TAKTX_SIGNING_PRIVATE_KEY is set but TAKTX_SIGNING_KEY_ID is missing. "
              + "Both must be provided together.");
    }
    return new WorkerSigningContext(privateKey, publicKey, keyId);
  }

  public String getPrivateKeyBase64() {
    return privateKeyBase64;
  }

  /** May be {@code null} when the public key was not configured or not needed. */
  public String getPublicKeyBase64() {
    return publicKeyBase64;
  }

  public String getKeyId() {
    return keyId;
  }
}
