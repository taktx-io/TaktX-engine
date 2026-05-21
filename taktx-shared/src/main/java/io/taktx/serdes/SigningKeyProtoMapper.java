/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.dto.KeyRole;
import io.taktx.dto.SigningKeyDTO;
import io.taktx.proto.SigningKeyMessage;
import java.time.Instant;

/** Shared DTO ↔ protobuf mapper for signing-key records. */
public final class SigningKeyProtoMapper {

  private SigningKeyProtoMapper() {}

  public static SigningKeyMessage toProto(SigningKeyDTO dto) {
    SigningKeyMessage.Builder builder = SigningKeyMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getKeyId() != null) {
      builder.setKeyId(dto.getKeyId());
    }
    if (dto.getPublicKeyBase64() != null) {
      builder.setPublicKeyBase64(dto.getPublicKeyBase64());
    }
    if (dto.getAlgorithm() != null) {
      builder.setAlgorithm(dto.getAlgorithm());
    }
    if (dto.getCreatedAt() != null) {
      builder.setCreatedAt(dto.getCreatedAt().toEpochMilli());
    }
    if (dto.getStatus() != null) {
      builder.setStatus(toProto(dto.getStatus()));
    }
    if (dto.getOwner() != null) {
      builder.setOwner(dto.getOwner());
    }
    builder.setRole(toProto(dto.effectiveRole()));
    if (dto.getRegistrationSignature() != null) {
      builder.setRegistrationSignature(dto.getRegistrationSignature());
    }
    return builder.build();
  }

  public static SigningKeyDTO toDto(SigningKeyMessage message) {
    if (message == null) {
      return null;
    }
    return SigningKeyDTO.builder()
        .keyId(emptyToNull(message.getKeyId()))
        .publicKeyBase64(emptyToNull(message.getPublicKeyBase64()))
        .algorithm(emptyToNull(message.getAlgorithm()))
        .createdAt(message.hasCreatedAt() ? Instant.ofEpochMilli(message.getCreatedAt()) : null)
        .status(toDto(message.getStatus()))
        .owner(emptyToNull(message.getOwner()))
        .role(toDto(message.getRole()))
        .registrationSignature(emptyToNull(message.getRegistrationSignature()))
        .build();
  }

  private static io.taktx.proto.KeyStatus toProto(SigningKeyDTO.KeyStatus status) {
    return switch (status) {
      case TRUSTED -> io.taktx.proto.KeyStatus.KEY_STATUS_TRUSTED;
      case REVOKED -> io.taktx.proto.KeyStatus.KEY_STATUS_REVOKED;
      case ACTIVE -> io.taktx.proto.KeyStatus.KEY_STATUS_ACTIVE;
    };
  }

  private static SigningKeyDTO.KeyStatus toDto(io.taktx.proto.KeyStatus status) {
    return switch (status) {
      case KEY_STATUS_TRUSTED -> SigningKeyDTO.KeyStatus.TRUSTED;
      case KEY_STATUS_REVOKED -> SigningKeyDTO.KeyStatus.REVOKED;
      case KEY_STATUS_UNSPECIFIED, KEY_STATUS_ACTIVE, UNRECOGNIZED ->
          SigningKeyDTO.KeyStatus.ACTIVE;
    };
  }

  private static io.taktx.proto.KeyRole toProto(KeyRole role) {
    return switch (role) {
      case ENGINE -> io.taktx.proto.KeyRole.KEY_ROLE_ENGINE;
      case PLATFORM -> io.taktx.proto.KeyRole.KEY_ROLE_PLATFORM;
      case CLIENT -> io.taktx.proto.KeyRole.KEY_ROLE_CLIENT;
    };
  }

  private static KeyRole toDto(io.taktx.proto.KeyRole role) {
    return switch (role) {
      case KEY_ROLE_ENGINE -> KeyRole.ENGINE;
      case KEY_ROLE_PLATFORM -> KeyRole.PLATFORM;
      case KEY_ROLE_UNSPECIFIED, KEY_ROLE_CLIENT, UNRECOGNIZED -> KeyRole.CLIENT;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
