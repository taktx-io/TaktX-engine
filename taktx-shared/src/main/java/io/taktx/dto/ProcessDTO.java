/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
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
