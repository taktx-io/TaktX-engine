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

/** Explicit runtime signing requirements for a namespace security policy. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class RequiredSigningDTO {
  @Builder.Default private boolean engineOutbound = false;
  @Builder.Default private boolean clientCommands = false;
  @Builder.Default private boolean workerResponses = false;

  public boolean isAnyRequired() {
    return engineOutbound || clientCommands || workerResponses;
  }
}
