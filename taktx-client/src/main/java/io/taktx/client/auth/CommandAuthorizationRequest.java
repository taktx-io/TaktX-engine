/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client.auth;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Structured context for outbound command JWT lookup. */
public record CommandAuthorizationRequest(
    CommandAuthorizationScope scope,
    @Nullable UUID processInstanceId,
    @Nullable String processDefinitionId,
    int processDefinitionVersion,
    @Nullable List<Long> elementInstanceIdPath) {
  public CommandAuthorizationRequest {
    elementInstanceIdPath =
        elementInstanceIdPath == null ? null : java.util.List.copyOf(elementInstanceIdPath);
  }

  public static CommandAuthorizationRequest startProcess(
      String processDefinitionId, int processDefinitionVersion, UUID processInstanceId) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.START_PROCESS,
        processInstanceId,
        processDefinitionId,
        processDefinitionVersion,
        null);
  }

  public static CommandAuthorizationRequest abortProcessInstance(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.ABORT_PROCESS_INSTANCE,
        processInstanceId,
        null,
        -1,
        elementInstanceIdPath);
  }
}
