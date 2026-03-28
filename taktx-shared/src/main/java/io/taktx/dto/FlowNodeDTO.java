/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@RegisterForReflection
public abstract class FlowNodeDTO extends FlowElementDTO {
  private Set<String> incoming;

  private Set<String> outgoing;

  protected FlowNodeDTO(
      String id, String parentId, String name, Set<String> incoming, Set<String> outgoing) {
    super(id, parentId, name);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
