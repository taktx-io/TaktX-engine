/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
@NoArgsConstructor
@RegisterForReflection
public class ProcessDTO extends RootElementDTO {

  public static final ProcessDTO NONE = new ProcessDTO(null, null, null, null);

  private String versionTag;

  private FlowElementsDTO flowElements;

  public ProcessDTO(String id, String parentId, String versionTag, FlowElementsDTO flowElements) {
    super(id, parentId);
    this.versionTag = versionTag;
    this.flowElements = flowElements;
  }
}
