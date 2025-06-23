/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class CatchEventInstanceDTO extends EventInstanceDTO {
  private CatchEventStateEnum state;

  private Set<ScheduleKeyDTO> scheduledKeys;

  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;

  private Set<EscalationSubscriptionDTO> escalationSubscriptions;

  private Set<ErrorSubscriptionDTO> errorSubscriptions;

  private boolean catchAllEscalations;

  private boolean catchAllErrors;

  @JsonIgnore
  @Override
  public boolean isWaiting() {
    return state == CatchEventStateEnum.WAITING;
  }
}
