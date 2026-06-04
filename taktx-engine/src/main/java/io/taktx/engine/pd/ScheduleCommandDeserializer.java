/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pd;

import io.taktx.dto.Constants;
import io.taktx.dto.MessageScheduleDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.serdes.MessageScheduleDtoDeserializer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Deserializes {@code schedule-commands} records into a {@link ScheduleCommandEnvelope} that
 * carries both the payload and the Ed25519 signature verification result.
 *
 * <p>Never throws — all error conditions are captured in {@link
 * ScheduleCommandEnvelope#signatureError()} so the downstream {@code ScheduleProcessor} can decide
 * how to handle them based on the current namespace security mode:
 *
 * <ul>
 *   <li>OPEN mode — no key resolver is registered; the envelope is returned with {@code
 *       signatureVerified=false} and no error, and the processor accepts it.
 *   <li>Signing active — the engine signs its own schedule commands; the deserializer verifies the
 *       {@code tx-sig} header and the processor enforces the result.
 * </ul>
 */
public class ScheduleCommandDeserializer implements Deserializer<ScheduleCommandEnvelope> {

  private final Deserializer<MessageScheduleDTO> delegate = new MessageScheduleDtoDeserializer();

  @Override
  public ScheduleCommandEnvelope deserialize(String topic, byte[] data) {
    return new ScheduleCommandEnvelope(decode(data), false, null, null);
  }

  @Override
  public ScheduleCommandEnvelope deserialize(String topic, Headers headers, byte[] data) {
    MessageScheduleDTO value = decode(data);

    Header sigHeader =
        headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      // No signature present — envelope carries unsigned state; mode enforcement happens
      // downstream.
      return new ScheduleCommandEnvelope(value, false, null, null);
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      return new ScheduleCommandEnvelope(
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
      return new ScheduleCommandEnvelope(
          value,
          false,
          keyId,
          "No EngineSigningKeysHolder key resolver available to verify schedule-commands record");
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      return new ScheduleCommandEnvelope(
          value,
          false,
          keyId,
          "Unknown or revoked signing keyId='" + keyId + "' — rejecting schedule-commands record");
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      byte[] payloadBytes = data != null ? data : new byte[0];
      if (!Ed25519Service.verify(payloadBytes, signatureBytes, publicKeyBase64)) {
        return new ScheduleCommandEnvelope(
            value,
            false,
            keyId,
            "Engine Ed25519 signature verification failed for schedule-commands keyId=" + keyId);
      }
    } catch (IllegalArgumentException e) {
      return new ScheduleCommandEnvelope(
          value,
          false,
          keyId,
          "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage());
    }

    return new ScheduleCommandEnvelope(value, true, keyId, null);
  }

  private MessageScheduleDTO decode(byte[] data) {
    if (data == null) {
      return null;
    }
    return delegate.deserialize(null, data);
  }
}
