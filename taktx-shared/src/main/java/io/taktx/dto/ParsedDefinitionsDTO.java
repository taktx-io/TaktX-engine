/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDefinitionsDTO extends DefinitionsTriggerDTO {
  private DefinitionsKey definitionsKey;

  private ProcessDTO rootProcess;

  private Map<String, MessageDTO> messages;

  private Map<String, EscalationDTO> escalations;

  private Map<String, ErrorDTO> errors;
}
