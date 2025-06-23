/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonFormat(shape = Shape.ARRAY)
public class DefinitionsKey {

  public static final DefinitionsKey NONE = new DefinitionsKey("", "");

  private String processDefinitionId;

  private String hash;

  public DefinitionsKey(String processDefinitionId, String hash) {
    this.processDefinitionId = processDefinitionId;
    this.hash = hash;
  }
}
