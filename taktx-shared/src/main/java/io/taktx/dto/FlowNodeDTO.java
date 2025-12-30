/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
