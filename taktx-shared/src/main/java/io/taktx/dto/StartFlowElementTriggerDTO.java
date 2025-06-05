/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StartFlowElementTriggerDTO extends ProcessInstanceTriggerDTO {

  private List<Long> parentElementInstanceIdPath;

  private String elementId;

  public StartFlowElementTriggerDTO(
      UUID processInstanceKey,
      List<Long> parentElementInstanceIdPath,
      String elementId,
      VariablesDTO variables) {
    super(processInstanceKey, variables);
    this.parentElementInstanceIdPath = parentElementInstanceIdPath;
    this.elementId = elementId;
  }
}
