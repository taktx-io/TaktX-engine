/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
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

  public static CommandAuthorizationRequest setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.SET_VARIABLE,
        processInstanceId,
        null,
        -1,
        elementInstanceIdPath);
  }
}
