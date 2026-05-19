/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserTaskResponseTriggerDTO extends ContinueFlowElementTriggerDTO {

  private String messageId;

  private UserTaskResponseResultDTO userTaskResponseResult;

  public UserTaskResponseTriggerDTO(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      UserTaskResponseResultDTO userTaskResponseResult,
      VariablesDTO variables) {
    this(processInstanceId, elementInstanceIdPath, null, userTaskResponseResult, variables);
  }

  public UserTaskResponseTriggerDTO(
      UUID processInstanceId,
      List<Long> elementInstanceIdPath,
      String messageId,
      UserTaskResponseResultDTO userTaskResponseResult,
      VariablesDTO variables) {
    super(processInstanceId, elementInstanceIdPath, null, variables);
    this.messageId = messageId;
    this.userTaskResponseResult = userTaskResponseResult;
  }
}
