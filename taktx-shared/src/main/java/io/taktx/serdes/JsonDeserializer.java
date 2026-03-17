/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.Constants;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningException;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.Getter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base CBOR/JSON deserializer with optional engine Ed25519 signature verification.
 *
 * <p>Three verification modes (checked in priority order):
 *
 * <ol>
 *   <li><b>Engine KTable:</b> registered via {@link EngineSigningKeysHolder} at engine startup.
 *       Uses the live {@code taktx-signing-keys} GlobalKTable — no extra consumer needed.
 *   <li><b>Worker SigningKeysStore:</b> registered via {@link SigningKeysStoreHolder} at worker
 *       startup. Standalone consumer, rotation-aware.
 *   <li><b>Static key:</b> set consumer property {@link #ENGINE_PUBLIC_KEY_CONFIG} (base64-encoded
 *       X.509 Ed25519 public key). Single key, no rotation support. For tests / simple deployments.
 * </ol>
 *
 * <p>When no key source is configured, signature headers are ignored (signing may be disabled).
 * When a signature header is present but verification fails — or the keyId is unknown/revoked — an
 * {@link IllegalStateException} is thrown to prevent offset commit.
 */
@Getter
public abstract class JsonDeserializer<T> implements Deserializer<T> {

  /**
   * Consumer config property for the engine Ed25519 public key (base64-encoded X.509 DER). Used for
   * static single-key configuration (tests, simple deployments). Superseded by {@link
   * EngineSigningKeysHolder} or {@link SigningKeysStoreHolder} in production.
   */
  public static final String ENGINE_PUBLIC_KEY_CONFIG = "taktx.engine.public.key";

  /**
   * Consumer / TaktX properties key that enables strict inbound signature enforcement for this
   * consumer. When {@code "true"}, inbound records that carry <em>no</em> {@code X-TaktX-Signature}
   * header are rejected with an {@link IllegalStateException} — i.e. unsigned records are treated
   * as a security violation even when they have no header at all.
   *
   * <p>When {@code false} (the default) unsigned records pass through silently, preserving
   * backward-compatibility with deployments where signing is disabled on the engine.
   *
   * <p>The property name is kept stable so workers, ingesters, and test consumers can opt into
   * strict verification with a single local configuration key.
   */
  public static final String SIGNING_REQUIRED_CONFIG = "taktx.security.signing.enabled";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private static final Logger log = LoggerFactory.getLogger(JsonDeserializer.class);

  private final Class<T> clazz;

  private final boolean shouldValidateSignature;

  /** Static fallback key — used when no dynamic key source is set. */
  private String enginePublicKeyBase64;

  /** Live worker key store — takes priority over the static key when set. */
  private SigningKeysStore signingKeysStore;

  /**
   * When {@code true}, records arriving without an {@code X-TaktX-Signature} header are rejected.
   * Set via {@link #SIGNING_REQUIRED_CONFIG} / {@code taktx.security.signing.enabled}.
   */
  private boolean localSigningRequired;

  protected JsonDeserializer(Class<T> clazz, boolean shouldValidateSignature) {
    this.shouldValidateSignature = shouldValidateSignature;
    this.clazz = clazz;
  }

  /**
   * Attaches a live {@link SigningKeysStore} to this deserializer. When set, all signature
   * verification uses the store for multi-key, rotation-aware lookups. Call this after {@code
   * SigningKeysStore.awaitReady()} has returned.
   */
  public void setSigningKeysStore(SigningKeysStore signingKeysStore) {
    this.signingKeysStore = signingKeysStore;
    log.info("{}: dynamic SigningKeysStore attached", getClass().getSimpleName());
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    // Local strict-verification flag: when true, unsigned inbound records are rejected even if they
    // simply have no signature header.
    Object signingEnabledFlag = configs.get(SIGNING_REQUIRED_CONFIG);
    localSigningRequired = "true".equalsIgnoreCase(String.valueOf(signingEnabledFlag));
    if (localSigningRequired && shouldValidateSignature) {
      log.info(
          "{}: inbound signature enforcement enabled ({}=true)",
          getClass().getSimpleName(),
          SIGNING_REQUIRED_CONFIG);
    }
    // Explicit static key takes highest priority — used in tests to pin the engine public key.
    Object key = configs.get(ENGINE_PUBLIC_KEY_CONFIG);
    if (key instanceof String s && !s.isBlank()) {
      enginePublicKeyBase64 = s;
      log.info(
          "{}: static Ed25519 key configured (keyLen={})", getClass().getSimpleName(), s.length());
      return; // static key wins — skip dynamic store lookup
    }
    // Dynamic mode: eagerly capture the store if it is already registered (e.g. consumer is
    // created after TaktXClient.start()). If not yet ready we fall through and the lazy lookup
    // in resolvePublicKey() will pick it up at first deserialize call.
    SigningKeysStore store = SigningKeysStoreHolder.get();
    if (store != null) {
      setSigningKeysStore(store);
    }
  }

  @Override
  public T deserialize(String topic, byte[] bytes) {
    try {
      return OBJECT_MAPPER.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Deserializes the record and verifies the {@code X-TaktX-Signature} header when a key source is
   * configured.
   *
   * <p>Records without a signature header pass through silently unless {@link
   * #SIGNING_REQUIRED_CONFIG} is {@code true}, in which case the missing header is treated as a
   * security violation and an {@link IllegalStateException} is thrown.
   *
   * @throws IllegalStateException if signature verification fails or if the header is absent and
   *     signing is required
   */
  @Override
  public T deserialize(String topic, Headers headers, byte[] data) {
    if (shouldValidateSignature && headers != null && hasKeySource()) {
      Header sigHeader = headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE);
      if (sigHeader != null && sigHeader.value() != null) {
        verifySignature(data, sigHeader);
      } else if (isSigningRequired()) {
        throw new IllegalStateException(
            "Inbound record on topic='"
                + topic
                + "' has no X-TaktX-Signature header but "
                + SIGNING_REQUIRED_CONFIG
                + "=true — rejecting unsigned record");
      }
    }
    return deserialize(topic, data);
  }

  private boolean hasKeySource() {
    return EngineSigningKeysHolder.get() != null
        || signingKeysStore != null
        || SigningKeysStoreHolder.get() != null
        || enginePublicKeyBase64 != null;
  }

  private boolean isSigningRequired() {
    return localSigningRequired || RuntimeConfigurationHolder.isSigningEnabled();
  }

  /**
   * Resolves the Ed25519 public key for the given keyId. Priority:
   *
   * <ol>
   *   <li>Static key (tests / simple deployments) — ignores keyId, single key only.
   *   <li>Engine KTable via {@link EngineSigningKeysHolder} — in-process engine path.
   *   <li>Instance-cached {@link SigningKeysStore} (set at configure time if store was ready).
   *   <li>Lazy lookup from {@link SigningKeysStoreHolder} — handles the case where the consumer was
   *       constructed before {@code TaktXClient.start()} finished initialising the store. The
   *       result is cached into {@link #signingKeysStore} to avoid repeated holder lookups.
   * </ol>
   */
  private String resolvePublicKey(String keyId) {
    // Static key configured explicitly (e.g. in tests) — use it directly, skip dynamic stores.
    if (enginePublicKeyBase64 != null) {
      return enginePublicKeyBase64;
    }
    EngineSigningKeysHolder.KeyResolver engineResolver = EngineSigningKeysHolder.get();
    if (engineResolver != null) {
      return engineResolver.resolvePublicKey(keyId);
    }
    // Use cached store if already captured at configure() time.
    if (signingKeysStore != null) {
      return signingKeysStore.getPublicKeyBase64(keyId);
    }
    // Lazy fallback: the consumer may have been constructed before start() initialised the store.
    // Pick it up now and cache it so subsequent calls are fast.
    SigningKeysStore liveStore = SigningKeysStoreHolder.get();
    if (liveStore != null) {
      setSigningKeysStore(liveStore);
      return liveStore.getPublicKeyBase64(keyId);
    }
    return null;
  }

  private void verifySignature(byte[] data, Header sigHeader) {
    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      throw new IllegalStateException(
          "Malformed X-TaktX-Signature header (expected '<keyId>.<base64sig>'): " + headerValue);
    }
    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);

    // ↓ breakpoint here — compare resolvedPublicKey with derivedPublicKeyBase64 in
    // MessageSigningService
    String resolvedPublicKey = resolvePublicKey(keyId);
    log.debug("Verifying signature keyId={} resolvedPublicKey={}", keyId, resolvedPublicKey);

    if (resolvedPublicKey == null) {
      throw new IllegalStateException(
          "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation");
    }
    try {
      byte[] sigBytes = Base64.getDecoder().decode(base64Sig);
      if (!Ed25519Service.verify(data, sigBytes, resolvedPublicKey)) {
        throw new IllegalStateException(
            "Engine Ed25519 signature verification failed for keyId=" + keyId);
      }
      log.trace("Signature verified for keyId={}", keyId);
    } catch (SigningException e) {
      throw new IllegalStateException(
          "Ed25519 signature error for keyId='" + keyId + "': " + e.getMessage(), e);
    }
  }
}
