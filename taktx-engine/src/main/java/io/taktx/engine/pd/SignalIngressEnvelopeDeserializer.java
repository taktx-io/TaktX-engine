/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pd;

import io.taktx.dto.Constants;
import io.taktx.dto.SignalDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.serdes.SignalProtoMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

public class SignalIngressEnvelopeDeserializer implements Deserializer<SignalIngressEnvelope> {

  @Override
  public SignalIngressEnvelope deserialize(String topic, byte[] data) {
    return new SignalIngressEnvelope(data, decode(data), false, null, null);
  }

  @Override
  public SignalIngressEnvelope deserialize(String topic, Headers headers, byte[] data) {
    SignalDTO value = decode(data);
    Header sigHeader =
        headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      return new SignalIngressEnvelope(data, value, false, null, null);
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      return new SignalIngressEnvelope(
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
      return new SignalIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "No EngineSigningKeysHolder key resolver available to verify signed signal record");
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      return new SignalIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation");
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      if (!Ed25519Service.verify(data, signatureBytes, publicKeyBase64)) {
        return new SignalIngressEnvelope(
            data,
            value,
            false,
            keyId,
            "Engine Ed25519 signature verification failed for keyId=" + keyId);
      }
    } catch (IllegalArgumentException e) {
      return new SignalIngressEnvelope(
          data,
          value,
          false,
          keyId,
          "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage());
    }

    return new SignalIngressEnvelope(data, value, true, keyId, null);
  }

  private SignalDTO decode(byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return SignalProtoMapper.toDto(io.taktx.proto.SignalEnvelope.parseFrom(data));
    } catch (com.google.protobuf.InvalidProtocolBufferException _) {
      return null;
    }
  }
}
