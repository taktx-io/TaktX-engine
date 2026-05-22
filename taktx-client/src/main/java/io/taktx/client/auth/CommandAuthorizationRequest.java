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

/**
 * Structured context for outbound command JWT lookup.
 *
 * @param scope logical command scope for which authorization is requested
 * @param processInstanceId target process instance ID when known
 * @param processDefinitionId target process-definition ID for start commands
 * @param processDefinitionVersion target process-definition version, or {@code -1} when not
 *     applicable
 * @param elementInstanceIdPath scope path for element-targeted commands, or {@code null} when not
 *     applicable
 */
public record CommandAuthorizationRequest(
    CommandAuthorizationScope scope,
    @Nullable UUID processInstanceId,
    @Nullable String processDefinitionId,
    int processDefinitionVersion,
    @Nullable List<Long> elementInstanceIdPath) {

  /** Canonical constructor that defensively copies the optional element-instance path. */
  public CommandAuthorizationRequest {
    elementInstanceIdPath =
        elementInstanceIdPath == null ? null : java.util.List.copyOf(elementInstanceIdPath);
  }

  /**
   * Creates an authorization request for a start-process command.
   *
   * @param processDefinitionId target process-definition ID
   * @param processDefinitionVersion target process-definition version, or {@code -1} for latest
   * @param processInstanceId client-generated process instance ID for the new instance
   * @return an authorization request for the start-process scope
   */
  public static CommandAuthorizationRequest startProcess(
      String processDefinitionId, int processDefinitionVersion, UUID processInstanceId) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.START_PROCESS,
        processInstanceId,
        processDefinitionId,
        processDefinitionVersion,
        null);
  }

  /**
   * Creates an authorization request for aborting a process or element instance.
   *
   * @param processInstanceId active process instance ID
   * @param elementInstanceIdPath path identifying the element instance to abort
   * @return an authorization request for the abort scope
   */
  public static CommandAuthorizationRequest abortProcessInstance(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.ABORT_PROCESS_INSTANCE,
        processInstanceId,
        null,
        -1,
        elementInstanceIdPath);
  }

  /**
   * Creates an authorization request for a set-variable command.
   *
   * @param processInstanceId active process instance ID
   * @param elementInstanceIdPath path identifying the scope whose variables will be updated
   * @return an authorization request for the set-variable scope
   */
  public static CommandAuthorizationRequest setVariable(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.SET_VARIABLE, processInstanceId, null, -1, elementInstanceIdPath);
  }

  /**
   * Creates an authorization request for completing a user task.
   *
   * @param processInstanceId active process instance ID
   * @param elementInstanceIdPath path identifying the user-task instance to complete
   * @return an authorization request for the user-task-complete scope
   */
  public static CommandAuthorizationRequest userTaskComplete(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.USER_TASK_COMPLETE,
        processInstanceId,
        null,
        -1,
        elementInstanceIdPath);
  }

  /**
   * Creates an authorization request for completing an external task.
   *
   * @param processInstanceId active process instance ID
   * @param elementInstanceIdPath path identifying the external-task instance to complete
   * @return an authorization request for the external-task-complete scope
   */
  public static CommandAuthorizationRequest externalTaskComplete(
      UUID processInstanceId, List<Long> elementInstanceIdPath) {
    return new CommandAuthorizationRequest(
        CommandAuthorizationScope.EXTERNAL_TASK_COMPLETE,
        processInstanceId,
        null,
        -1,
        elementInstanceIdPath);
  }
}
