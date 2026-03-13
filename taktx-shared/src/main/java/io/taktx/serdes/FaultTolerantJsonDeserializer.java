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
import io.taktx.security.SigningException;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
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
 * A fault-tolerant variant of {@link JsonDeserializer} that deserializes into {@link
 * DeserializationResult}{@code <T>} instead of throwing on signature failures.
 *
 * <p>The body is always decoded first. Signature verification is then attempted separately. If it
 * fails the body is still returned inside the result so the consumer can use it to identify the
 * process instance and report a BPMN error or incident back to the engine.
 *
 * <p>Only body-decode failures (corrupt CBOR / wrong type) result in a {@link
 * DeserializationResult#failure} with a {@code null} value.
 */
@Getter
public abstract class FaultTolerantJsonDeserializer<T>
    implements Deserializer<DeserializationResult<T>> {

  public static final String ENGINE_PUBLIC_KEY_CONFIG = "taktx.engine.public.key";
  public static final String SIGNING_REQUIRED_CONFIG = "taktx.security.signing.enabled";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
  private static final Logger log = LoggerFactory.getLogger(FaultTolerantJsonDeserializer.class);

  private final Class<T> clazz;
  private final boolean shouldValidateSignature;

  private String enginePublicKeyBase64;
  private SigningKeysStore signingKeysStore;
  private boolean signingRequired;

  protected FaultTolerantJsonDeserializer(Class<T> clazz, boolean shouldValidateSignature) {
    this.clazz = clazz;
    this.shouldValidateSignature = shouldValidateSignature;
  }

  public void setSigningKeysStore(SigningKeysStore signingKeysStore) {
    this.signingKeysStore = signingKeysStore;
    log.info("{}: dynamic SigningKeysStore attached", getClass().getSimpleName());
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    Object signingEnabledFlag = configs.get(SIGNING_REQUIRED_CONFIG);
    signingRequired = "true".equalsIgnoreCase(String.valueOf(signingEnabledFlag));
    Object key = configs.get(ENGINE_PUBLIC_KEY_CONFIG);
    if (key instanceof String s && !s.isBlank()) {
      enginePublicKeyBase64 = s;
      return;
    }
    SigningKeysStore store = SigningKeysStoreHolder.get();
    if (store != null) {
      setSigningKeysStore(store);
    }
  }

  /** Header-unaware path — no signature check possible, body decode only. */
  @Override
  public DeserializationResult<T> deserialize(String topic, byte[] data) {
    return decodeBody(data);
  }

  /**
   * Header-aware path. Always decodes the body first, then attempts signature verification
   * independently. A signature failure yields {@link DeserializationResult#bodyDecodedWithError} so
   * the consumer still has the payload to work with.
   */
  @Override
  public DeserializationResult<T> deserialize(String topic, Headers headers, byte[] data) {
    // 1. Decode the body first — always, regardless of signature status.
    DeserializationResult<T> bodyResult = decodeBody(data);
    if (!bodyResult.hasValue()) {
      // Body itself is unreadable — nothing more we can do.
      return bodyResult;
    }

    // 2. Attempt signature verification if applicable.
    if (shouldValidateSignature && headers != null && hasKeySource()) {
      Header sigHeader = headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE);
      if (sigHeader != null && sigHeader.value() != null) {
        String sigError = tryVerifySignature(data, sigHeader);
        if (sigError != null) {
          log.warn(
              "Signature verification failed on topic='{}': {} — body decoded, returning error result",
              topic,
              sigError);
          return DeserializationResult.bodyDecodedWithError(bodyResult.getValue(), sigError);
        }
      } else if (signingRequired) {
        String error =
            "Inbound record on topic='"
                + topic
                + "' has no X-TaktX-Signature header but "
                + SIGNING_REQUIRED_CONFIG
                + "=true — rejecting unsigned record";
        log.warn(error);
        return DeserializationResult.bodyDecodedWithError(bodyResult.getValue(), error);
      }
    }

    return bodyResult; // success
  }

  // ── private helpers ──────────────────────────────────────────────────────────

  private DeserializationResult<T> decodeBody(byte[] data) {
    try {
      T value = OBJECT_MAPPER.readValue(data, clazz);
      return DeserializationResult.success(value);
    } catch (Exception e) {
      String msg = "Failed to decode body as " + clazz.getSimpleName() + ": " + e.getMessage();
      log.error(msg, e);
      return DeserializationResult.failure(msg);
    }
  }

  private boolean hasKeySource() {
    return EngineSigningKeysHolder.get() != null
        || signingKeysStore != null
        || SigningKeysStoreHolder.get() != null
        || enginePublicKeyBase64 != null;
  }

  private String resolvePublicKey(String keyId) {
    if (enginePublicKeyBase64 != null) return enginePublicKeyBase64;
    EngineSigningKeysHolder.KeyResolver engineResolver = EngineSigningKeysHolder.get();
    if (engineResolver != null) return engineResolver.resolvePublicKey(keyId);
    if (signingKeysStore != null) return signingKeysStore.getPublicKeyBase64(keyId);
    SigningKeysStore liveStore = SigningKeysStoreHolder.get();
    if (liveStore != null) {
      setSigningKeysStore(liveStore);
      return liveStore.getPublicKeyBase64(keyId);
    }
    return null;
  }

  /**
   * Returns {@code null} on success, or an error message string on failure. Never throws — all
   * errors are captured as strings.
   */
  private String tryVerifySignature(byte[] data, Header sigHeader) {
    try {
      String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
      int dot = headerValue.indexOf('.');
      if (dot < 0) {
        return "Malformed X-TaktX-Signature header (expected '<keyId>.<base64sig>'): "
            + headerValue;
      }
      String keyId = headerValue.substring(0, dot);
      String base64Sig = headerValue.substring(dot + 1);

      String resolvedPublicKey = resolvePublicKey(keyId);
      log.debug("Verifying signature keyId={} resolvedPublicKey={}", keyId, resolvedPublicKey);

      if (resolvedPublicKey == null) {
        return "Unknown or revoked signing keyId='"
            + keyId
            + "' — no matching key found in signing key store";
      }

      byte[] sigBytes = Base64.getDecoder().decode(base64Sig);
      if (!Ed25519Service.verify(data, sigBytes, resolvedPublicKey)) {
        return "Engine Ed25519 signature verification failed for keyId=" + keyId;
      }
      return null; // success
    } catch (SigningException e) {
      return "Ed25519 signature error: " + e.getMessage();
    } catch (Exception e) {
      return "Unexpected signature verification error: " + e.getMessage();
    }
  }
}
