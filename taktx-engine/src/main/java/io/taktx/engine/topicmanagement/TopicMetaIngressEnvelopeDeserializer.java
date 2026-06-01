/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.topicmanagement;

import io.taktx.dto.Constants;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.serdes.TopicMetaProtoMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

public class TopicMetaIngressEnvelopeDeserializer implements Deserializer<TopicMetaIngressEnvelope> {

  @Override
  public TopicMetaIngressEnvelope deserialize(String topic, byte[] data) {
    return new TopicMetaIngressEnvelope(data, decode(data), false, null, null);
  }

  @Override
  public TopicMetaIngressEnvelope deserialize(String topic, Headers headers, byte[] data) {
    TopicMetaDTO value = decode(data);
    Header sigHeader = headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      return new TopicMetaIngressEnvelope(data, value, false, null, null);
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      return new TopicMetaIngressEnvelope(
          data,
          value,
          false,
          null,
          "Malformed "
              + Constants.HEADER_ENGINE_SIGNATURE
              + " header (expected '<keyId>.<base64sig>'): "
              + headerValue);
    }

    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);
    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver == null) {
      return new TopicMetaIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "No EngineSigningKeysHolder key resolver available to verify signed topic-meta-requested record");
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      return new TopicMetaIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation");
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      byte[] payloadBytes = data != null ? data : new byte[0];
      if (!Ed25519Service.verify(payloadBytes, signatureBytes, publicKeyBase64)) {
        return new TopicMetaIngressEnvelope(
            data,
            value,
            false,
            keyId,
            "Engine Ed25519 signature verification failed for keyId=" + keyId);
      }
    } catch (IllegalArgumentException e) {
      return new TopicMetaIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage());
    }

    return new TopicMetaIngressEnvelope(data, value, true, keyId, null);
  }

  private TopicMetaDTO decode(byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return TopicMetaProtoMapper.toDto(io.taktx.proto.TopicMetaMessage.parseFrom(data));
    } catch (com.google.protobuf.InvalidProtocolBufferException _) {
      return null;
    }
  }
}

