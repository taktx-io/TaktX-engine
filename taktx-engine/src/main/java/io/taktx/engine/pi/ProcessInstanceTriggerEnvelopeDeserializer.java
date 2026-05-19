/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import com.google.protobuf.InvalidProtocolBufferException;
import io.taktx.dto.Constants;
import io.taktx.dto.ProcessInstanceTriggerDTO;
import io.taktx.security.Ed25519Service;
import io.taktx.security.EngineSigningKeysHolder;
import io.taktx.serdes.ProcessInstanceTriggerProtoMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

public class ProcessInstanceTriggerEnvelopeDeserializer
    implements Deserializer<ProcessInstanceTriggerEnvelope> {

  private static final String JSON_UNICODE_ESCAPE_PREFIX = "\\u";

  @Override
  public ProcessInstanceTriggerEnvelope deserialize(String topic, byte[] data) {
    return new ProcessInstanceTriggerEnvelope(data, decode(data), false, null);
  }

  @Override
  public ProcessInstanceTriggerEnvelope deserialize(String topic, Headers headers, byte[] data) {
    ProcessInstanceTriggerDTO trigger = decode(data);
    String replayRoutingKeyHint = extractReplayRoutingKeyHint(headers);
    Header sigHeader =
        headers != null ? headers.lastHeader(Constants.HEADER_ENGINE_SIGNATURE) : null;
    if (sigHeader == null || sigHeader.value() == null) {
      return new ProcessInstanceTriggerEnvelope(data, trigger, false, null)
          .withReplayRoutingKeyHint(replayRoutingKeyHint);
    }

    String headerValue = new String(sigHeader.value(), StandardCharsets.UTF_8);
    int dot = headerValue.indexOf('.');
    if (dot < 0) {
      return new ProcessInstanceTriggerEnvelope(
              data,
              trigger,
              false,
              null,
              "Malformed "
                  + Constants.HEADER_ENGINE_SIGNATURE
                  + " header (expected '<keyId>.<base64sig>'): "
                  + headerValue)
          .withReplayRoutingKeyHint(replayRoutingKeyHint);
    }
    String keyId = headerValue.substring(0, dot);
    String base64Sig = headerValue.substring(dot + 1);

    EngineSigningKeysHolder.KeyResolver keyResolver = EngineSigningKeysHolder.get();
    if (keyResolver == null) {
      return new ProcessInstanceTriggerEnvelope(
              data,
              trigger,
              false,
              keyId,
              "No EngineSigningKeysHolder key resolver available to verify signed process-instance-trigger record")
          .withReplayRoutingKeyHint(replayRoutingKeyHint);
    }

    String publicKeyBase64 = keyResolver.resolvePublicKey(keyId);
    if (publicKeyBase64 == null) {
      return new ProcessInstanceTriggerEnvelope(
              data,
              trigger,
              false,
              keyId,
              "Unknown or revoked signing keyId='" + keyId + "' — treating as security violation")
          .withReplayRoutingKeyHint(replayRoutingKeyHint);
    }

    try {
      byte[] signatureBytes = Base64.getDecoder().decode(base64Sig);
      if (!Ed25519Service.verify(data, signatureBytes, publicKeyBase64)) {
        return new ProcessInstanceTriggerEnvelope(
                data,
                trigger,
                false,
                keyId,
                "Engine Ed25519 signature verification failed for keyId=" + keyId)
            .withReplayRoutingKeyHint(replayRoutingKeyHint);
      }
    } catch (IllegalArgumentException e) {
      return new ProcessInstanceTriggerEnvelope(
              data,
              trigger,
              false,
              keyId,
              "Malformed base64 signature for keyId=" + keyId + ": " + e.getMessage())
          .withReplayRoutingKeyHint(replayRoutingKeyHint);
    }

    return new ProcessInstanceTriggerEnvelope(data, trigger, true, keyId)
        .withReplayRoutingKeyHint(replayRoutingKeyHint);
  }

  private static String extractReplayRoutingKeyHint(Headers headers) {
    Header authHeader = headers != null ? headers.lastHeader(Constants.HEADER_AUTHORIZATION) : null;
    if (authHeader == null || authHeader.value() == null || authHeader.value().length == 0) {
      return null;
    }
    String rawJwt = new String(authHeader.value(), StandardCharsets.UTF_8);
    if (rawJwt.isBlank()) {
      return null;
    }
    try {
      String[] parts = rawJwt.split("\\.");
      if (parts.length < 2) {
        return "jwt:" + sha256(rawJwt);
      }
      String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      String issuer = textValue(decodedPayload, "iss");
      String auditId = textValue(decodedPayload, "auditId");
      if (issuer != null && !issuer.isBlank() && auditId != null && !auditId.isBlank()) {
        return issuer + ":" + auditId;
      }
    } catch (Exception _) {
      // Fall back to a stable raw-token hash for malformed/unexpected payloads.
    }
    return "jwt:" + sha256(rawJwt);
  }

  private static String textValue(String jsonPayload, String fieldName) {
    if (jsonPayload == null || jsonPayload.isBlank()) {
      return null;
    }
    Pattern pattern =
        Pattern.compile(
            "\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    Matcher matcher = pattern.matcher(jsonPayload);
    return matcher.find() ? unescapeJsonString(matcher.group(1)) : null;
  }

  private static String unescapeJsonString(String value) {
    if (value == null || value.indexOf('\\') < 0) {
      return value;
    }
    StringBuilder result = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current != '\\' || index == value.length() - 1) {
        result.append(current);
        continue;
      }
      char escaped = value.charAt(++index);
      switch (escaped) {
        case '"' -> result.append('"');
        case '\\' -> result.append('\\');
        case '/' -> result.append('/');
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> {
          if (index + 4 >= value.length()) {
            throw new IllegalArgumentException(
                "Invalid JSON unicode escape: " + JSON_UNICODE_ESCAPE_PREFIX + value.substring(index + 1));
          }
          String hex = value.substring(index + 1, index + 5);
          result.append((char) Integer.parseInt(hex, 16));
          index += 4;
        }
        default -> result.append(escaped);
      }
    }
    return result.toString();
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte current : digest) {
        hex.append(String.format("%02x", current));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private ProcessInstanceTriggerDTO decode(byte[] data) {
    if (data == null) {
      return null;
    }
    try {
      return ProcessInstanceTriggerProtoMapper.toDto(
          io.taktx.proto.ProcessInstanceTriggerEnvelope.parseFrom(data));
    } catch (InvalidProtocolBufferException _) {
      // Fall back to null, or partial object later
      return null;
    }
  }
}
