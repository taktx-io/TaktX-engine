/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@RegisterForReflection
public abstract class ActivityInstanceDTO extends FlowNodeInstanceDTO {
  private ActtivityStateEnum state;

  private Set<Long> boundaryEventIds;

  private boolean iteration = false;

  private long nextIterationId;

  private JsonNode inputElement;

  private JsonNode outputElement;

  private int loopCnt;

  @Override
  public boolean isTerminated() {
    return state == ActtivityStateEnum.TERMINATED;
  }

  @Override
  public boolean isFailed() {
    return state == ActtivityStateEnum.FAILED;
  }

  @Override
  public boolean isWaiting() {
    return state == ActtivityStateEnum.WAITING;
  }
}
