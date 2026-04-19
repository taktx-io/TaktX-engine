/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import io.taktx.dto.Constants;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

public class ScheduleCommandDeserializer implements Deserializer<MessageScheduleDTO> {

  private final Deserializer<MessageScheduleDTO> delegate =
      new ObjectMapperSerde<>(MessageScheduleDTO.class).deserializer();

  @Override
  public MessageScheduleDTO deserialize(String topic, byte[] data) {
    return decode(data);
  }

  @Override
  public MessageScheduleDTO deserialize(String topic, Headers headers, byte[] data) {
    Header sigHeader =
        headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      throw new IllegalStateException(
          "Inbound record on topic='"
              + topic
              + "' has no X-TaktX-Signature header — rejecting unsigned schedule command");
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      throw new IllegalStateException(
          "Malformed X-TaktX-Signature header (expected '<keyId>.<base64sig>'): " + headerValue);
    }

    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);
    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver == null) {
      throw new IllegalStateException(
          "No EngineSigningKeysHolder key resolver available to verify signed schedule-commands record");
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      throw new IllegalStateException(
          "Unknown or revoked signing keyId='" + keyId + "' — rejecting schedule-commands record");
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      byte[] payloadBytes = data != null ? data : new byte[0];
      if (!Ed25519Service.verify(payloadBytes, signatureBytes, publicKeyBase64)) {
        throw new IllegalStateException(
            "Engine Ed25519 signature verification failed for schedule-commands keyId=" + keyId);
      }
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage(), e);
    }

    return decode(data);
  }

  private MessageScheduleDTO decode(byte[] data) {
    if (data == null) {
      return null;
    }
    return delegate.deserialize(null, data);
  }
}
