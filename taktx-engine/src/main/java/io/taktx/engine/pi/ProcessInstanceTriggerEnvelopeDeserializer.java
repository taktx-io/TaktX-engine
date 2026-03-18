/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.taktx.dto.Constants;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

public class ProcessInstanceTriggerEnvelopeDeserializer
    implements Deserializer<ProcessInstanceTriggerEnvelope> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new CBORFactory());

  @Override
  public ProcessInstanceTriggerEnvelope deserialize(String topic, byte[] data) {
    return new ProcessInstanceTriggerEnvelope(decode(data), false, null);
  }

  @Override
  public ProcessInstanceTriggerEnvelope deserialize(String topic, Headers headers, byte[] data) {
    ProcessInstanceTriggerDTO trigger = decode(data);
    Header sigHeader =
        headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      return new ProcessInstanceTriggerEnvelope(trigger, false, null);
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      return new ProcessInstanceTriggerEnvelope(
          trigger,
          false,
          null,
          "Malformed X-TaktX-Signature header (expected '<keyId>.<base64sig>'): " + headerValue);
    }
    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);

    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver == null) {
      return new ProcessInstanceTriggerEnvelope(
          trigger,
          false,
          keyId,
          "No EngineSigningKeysHolder key resolver available to verify signed process-instance-trigger record");
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      return new ProcessInstanceTriggerEnvelope(
          trigger,
          false,
          keyId,
          "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation");
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      if (!Ed25519Service.verify(data, signatureBytes, publicKeyBase64)) {
        return new ProcessInstanceTriggerEnvelope(
            trigger,
            false,
            keyId,
            "Engine Ed25519 signature verification failed for keyId=" + keyId);
      }
    } catch (IllegalArgumentException e) {
      return new ProcessInstanceTriggerEnvelope(
          trigger,
          false,
          keyId,
          "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage());
    }

    return new ProcessInstanceTriggerEnvelope(trigger, true, keyId);
  }

  private ProcessInstanceTriggerDTO decode(byte[] data) {
    try {
      return OBJECT_MAPPER.readValue(data, ProcessInstanceTriggerDTO.class);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
