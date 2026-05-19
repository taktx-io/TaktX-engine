/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import io.taktx.CleanupPolicy;
import io.taktx.dto.TopicMetaDTO;
import io.taktx.proto.TopicMetaMessage;

/** Shared DTO ↔ protobuf mapper for topic metadata records. */
public final class TopicMetaProtoMapper {

  private TopicMetaProtoMapper() {}

  public static TopicMetaMessage toProto(TopicMetaDTO dto) {
    TopicMetaMessage.Builder builder = TopicMetaMessage.newBuilder();
    if (dto == null) {
      return builder.build();
    }
    if (dto.getTopicName() != null) {
      builder.setTopicName(dto.getTopicName());
    }
    builder.setNrPartitions(dto.getNrPartitions());
    if (dto.getCleanupPolicy() != null) {
      builder.setCleanupPolicy(toProto(dto.getCleanupPolicy()));
    }
    builder.setReplicationFactor(dto.getReplicationFactor());
    if (dto.getMessageId() != null) {
      builder.setMessageId(dto.getMessageId());
    }
    return builder.build();
  }

  public static TopicMetaDTO toDto(TopicMetaMessage message) {
    if (message == null) {
      return null;
    }
    return new TopicMetaDTO(
        emptyToNull(message.getTopicName()),
        message.getNrPartitions(),
        toDto(message.getCleanupPolicy()),
        (short) message.getReplicationFactor(),
        emptyToNull(message.getMessageId()));
  }

  private static io.taktx.proto.CleanupPolicy toProto(CleanupPolicy cleanupPolicy) {
    return switch (cleanupPolicy) {
      case COMPACT -> io.taktx.proto.CleanupPolicy.CLEANUP_POLICY_COMPACT;
      case DELETE -> io.taktx.proto.CleanupPolicy.CLEANUP_POLICY_DELETE;
    };
  }

  private static CleanupPolicy toDto(io.taktx.proto.CleanupPolicy cleanupPolicy) {
    return switch (cleanupPolicy) {
      case CLEANUP_POLICY_COMPACT -> CleanupPolicy.COMPACT;
      case CLEANUP_POLICY_UNSPECIFIED, CLEANUP_POLICY_DELETE, UNRECOGNIZED -> CleanupPolicy.DELETE;
    };
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
