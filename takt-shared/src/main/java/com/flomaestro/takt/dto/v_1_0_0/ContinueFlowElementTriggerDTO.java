/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContinueFlowElementTriggerDTO extends ProcessInstanceTriggerDTO {

  @JsonProperty("ifi")
  private String inputFlowId;

  @JsonProperty("eiip")
  private List<Long> elementInstanceIdPath;

  public ContinueFlowElementTriggerDTO(
      UUID processInstanceKey,
      List<Long> elementInstanceIdPath,
      String inputFlowId,
      VariablesDTO variables) {
    super(processInstanceKey, List.of(), variables);
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.inputFlowId = inputFlowId;
  }
}
