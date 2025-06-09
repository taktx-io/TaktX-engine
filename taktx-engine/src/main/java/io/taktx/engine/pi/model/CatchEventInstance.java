/*
 *
 *  * TaktX - A high-performance BPMN engine
 *  * Copyright (c) 2025 TaktX B.V. All rights reserved.
 *  * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 *  * Free use is permitted with up to 3 Kafka partitions. See LICENSE file for details.
 *  * For commercial use or more partitions and features, contact [info@taktx.io] or [https://www.taktx.io/contact].
 *
 */

package io.taktx.engine.pi.model;

import io.taktx.dto.CatchEventStateEnum;
import io.taktx.dto.MessageEventKeyDTO;
import io.taktx.dto.ScheduleKeyDTO;
import io.taktx.engine.pd.model.CatchEvent;
import io.taktx.engine.pd.model.ErrorEventDefinition;
import io.taktx.engine.pd.model.EscalationEventDefinition;
import io.taktx.engine.pd.model.EventSignal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public abstract class CatchEventInstance<N extends CatchEvent> extends EventInstance<N>
    implements ReceivingMessageInstance, FlowNodeInstanceWithScheduleKeys {
  private CatchEventStateEnum state = null;
  private boolean stateChanged = false;
  private boolean wasWaiting = false;
  private boolean wasNew = false;
  private Set<ScheduleKeyDTO> scheduledKeys = new HashSet<>();
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys = new HashMap<>();
  private Set<EscalationSubscription> escalationSubscriptions = new HashSet<>();
  private Set<ErrorSubscription> errorSubscriptions = new HashSet<>();
  private boolean catchAllEscalations = false;
  private boolean catchAllErrors = false;

  protected CatchEventInstance(
      FlowNodeInstance<?> parentInstance, N flowNode, long elementInstanceId) {
    super(parentInstance, flowNode, elementInstanceId);
  }

  @Override
  public void setInitialState() {
    setState(CatchEventStateEnum.INITIAL);
  }

  @Override
  public boolean stateAllowsStart() {
    return state == CatchEventStateEnum.INITIAL;
  }

  @Override
  public void setStartedState() {
    setState(CatchEventStateEnum.STARTED);
  }

  @Override
  public boolean wasAwaiting() {
    return wasWaiting;
  }

  @Override
  public boolean wasNew() {
    return wasNew;
  }

  public void setState(CatchEventStateEnum state) {
    if (this.state == null && state == CatchEventStateEnum.INITIAL) {
      setDirty();
    }
    if (this.state != null && this.state != state) {
      stateChanged = true;
      setDirty();
    }
    if (this.state == CatchEventStateEnum.INITIAL && this.state != state) {
      wasNew = true;
    }
    if (state == CatchEventStateEnum.WAITING) {
      wasWaiting = true;
    }
    this.state = state;
  }

  @Override
  public boolean stateChanged() {
    return stateChanged;
  }

  @Override
  public boolean stateAllowsContinue() {
    return state == CatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isNotAwaiting() {
    return state != CatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isAwaiting() {
    return state == CatchEventStateEnum.WAITING;
  }

  @Override
  public boolean isCompleted() {
    return state == CatchEventStateEnum.FINISHED || state == CatchEventStateEnum.TERMINATED;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == CatchEventStateEnum.WAITING || state == CatchEventStateEnum.INITIAL;
  }

  @Override
  public void terminate() {
    // Do nothing
    if (stateAllowsTerminate()) {
      setState(CatchEventStateEnum.TERMINATED);
    }
  }

  public void addScheduledKey(ScheduleKeyDTO scheduledKey) {
    this.scheduledKeys.add(scheduledKey);
  }

  public void addMessageSubscriptionWithCorrelationKey(
      MessageEventKeyDTO messageEventKey, String correlationKey) {
    this.messageEventKeys
        .computeIfAbsent(messageEventKey, k -> new HashSet<>())
        .add(correlationKey);
  }

  public void addEscalationSubscription(EscalationEventDefinition escalationEventDefinition) {
    if (escalationEventDefinition.getReferencedEscalation() != null) {
      this.catchAllEscalations = false;
      escalationSubscriptions.add(
          new EscalationSubscription(
              escalationEventDefinition.getReferencedEscalation().escalationCode()));
    } else {
      this.catchAllEscalations = true;
    }
  }

  public void addErrorSubscription(ErrorEventDefinition errorEventDefinition) {
    if (errorEventDefinition.getReferencedError() != null) {
      errorSubscriptions.add(
          new ErrorSubscription(errorEventDefinition.getReferencedError().code()));
    } else {
      this.catchAllErrors = true;
    }
  }

  public void clearEscalationSubscriptions() {
    escalationSubscriptions.clear();
  }

  public void clearErrorSubscriptions() {
    errorSubscriptions.clear();
  }

  public boolean matchesEvent(EventSignal event) {
    if (event instanceof EscalationEventSignal escalationEventSignal) {
      return escalationSubscriptions.stream()
          .anyMatch(
              escalationSubscription -> escalationSubscription.matchesEvent(escalationEventSignal));
    } else if (event instanceof ErrorEventSignal errorEventSignal) {
      return errorSubscriptions.stream()
          .anyMatch(errorSubscription -> errorSubscription.matchesEvent(errorEventSignal));
    }
    return false;
  }

  public boolean matchesEventCatchAll(EventSignal event) {
    if (event instanceof EscalationEventSignal) {
      return isCatchAllEscalations();
    } else if (event instanceof ErrorEventSignal) {
      return isCatchAllErrors();
    }
    return false;
  }
}
