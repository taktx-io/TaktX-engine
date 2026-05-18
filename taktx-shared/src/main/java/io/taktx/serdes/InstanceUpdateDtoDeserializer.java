/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.Constants;
import io.taktx.dto.InstanceUpdateDTO;
import io.taktx.proto.InstanceUpdateEnvelope;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningException;
import io.taktx.security.SigningKeysStore;
import io.taktx.security.SigningKeysStoreHolder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.Getter;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deserializes protobuf-backed instance updates into DTOs with optional signature verification. */
@Getter
public class InstanceUpdateDtoDeserializer implements Deserializer<InstanceUpdateDTO> {

  public static final String ENGINE_PUBLIC_KEY_CONFIG = JsonDeserializer.ENGINE_PUBLIC_KEY_CONFIG;
  public static final String SIGNING_REQUIRED_CONFIG = JsonDeserializer.SIGNING_REQUIRED_CONFIG;

  private static final Logger log = LoggerFactory.getLogger(InstanceUpdateDtoDeserializer.class);

  private final Class<InstanceUpdateDTO> clazz = InstanceUpdateDTO.class;

  private String enginePublicKeyBase64;
  private SigningKeysStore signingKeysStore;
  private boolean localSigningRequired;

  public void setSigningKeysStore(SigningKeysStore signingKeysStore) {
    this.signingKeysStore = signingKeysStore;
    log.info("{}: dynamic SigningKeysStore attached", getClass().getSimpleName());
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    Object signingEnabledFlag = configs.get(SIGNING_REQUIRED_CONFIG);
    localSigningRequired = "true".equalsIgnoreCase(String.valueOf(signingEnabledFlag));
    Object key = configs.get(ENGINE_PUBLIC_KEY_CONFIG);
    if (key instanceof String s && !s.isBlank()) {
      enginePublicKeyBase64 = s;
      log.info(
          "{}: static Ed25519 key configured (keyLen={})", getClass().getSimpleName(), s.length());
      return;
    }
    SigningKeysStore store = SigningKeysStoreHolder.get();
    if (store != null) {
      setSigningKeysStore(store);
    }
  }

  @Override
  public InstanceUpdateDTO deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return InstanceUpdateProtoMapper.toDto(InstanceUpdateEnvelope.parseFrom(data));
    } catch (InvalidProtocolBufferException e) {
      throw new SerializationException("Failed to deserialize InstanceUpdateEnvelope", e);
    }
  }

  @Override
  public InstanceUpdateDTO deserialize(String topic, Headers headers, byte[] data) {
    if (headers != null && hasKeySource()) {
      Header sigHeader = headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE);
      if (sigHeader != null && sigHeader.value() != null) {
        verifySignature(data, sigHeader);
      } else if (isSigningRequired()) {
        throw new IllegalStateException(
            "Inbound record on topic='"
                + topic
                + "' has no "
                + Constants.HEADER_ENGINE_SIGNATURE
                + " header but "
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

  private String resolvePublicKey(String keyId) {
    if (enginePublicKeyBase64 != null) {
      return enginePublicKeyBase64;
    }
    EngineSigningKeysHolder.KeyResolver engineResolver = EngineSigningKeysHolder.get();
    if (engineResolver != null) {
      return engineResolver.resolvePublicKey(keyId);
    }
    if (signingKeysStore != null) {
      return signingKeysStore.getPublicKeyBase64(keyId);
    }
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
          "Malformed "
              + Constants.HEADER_ENGINE_SIGNATURE
              + " header (expected '<keyId>.<base64sig>'): "
              + headerValue);
    }
    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);

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
