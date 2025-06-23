/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ExternalTaskDTO extends TaskDTO {

  private String workerDefinition;

  private String retries;

  private String implementation;

  private Map<String, String> headers;

  protected ExternalTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping,
      String workerDefinition,
      String retries,
      String implementation,
      Map<String, String> headers) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.workerDefinition = workerDefinition;
    this.retries = retries;
    this.implementation = implementation;
    this.headers = headers;
  }
}
