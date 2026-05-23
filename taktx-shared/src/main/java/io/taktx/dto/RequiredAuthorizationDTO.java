/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Explicit runtime authorization requirements for a namespace security policy. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class RequiredAuthorizationDTO {
  @Builder.Default private boolean startCommands = false;
  @Builder.Default private boolean externalTaskCompletion = false;
  @Builder.Default private boolean userTaskCompletion = false;

  public boolean isAnyRequired() {
    return startCommands || externalTaskCompletion || userTaskCompletion;
  }
}
