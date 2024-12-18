package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.CatchEvent;
import com.flomaestro.engine.pd.model.ErrorEventDefinition;
import com.flomaestro.engine.pd.model.EscalationEventDefinition;
import com.flomaestro.engine.pd.model.EventSignal;
import com.flomaestro.takt.dto.v_1_0_0.CatchEventStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.MessageEventKeyDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduleKeyDTO;
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
  private CatchEventStateEnum state;

  private Set<ScheduleKeyDTO> scheduledKeys;
  private Map<MessageEventKeyDTO, Set<String>> messageEventKeys;
  private Set<EscalationSubscription> escalationSubscriptions;
  private Set<ErrorSubscription> errorSubscriptions;
  private boolean catchAllEscalations;
  private boolean catchAllErrors;

  protected CatchEventInstance(FlowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
    state = CatchEventStateEnum.READY;
    scheduledKeys = new HashSet<>();
    messageEventKeys = new HashMap<>();
    escalationSubscriptions = new HashSet<>();
    errorSubscriptions = new HashSet<>();
    catchAllEscalations = false;
    catchAllErrors = false;
  }

  @Override
  public boolean stateAllowsStart() {
    return state == CatchEventStateEnum.READY;
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
  public boolean isCompleted() {
    return state == CatchEventStateEnum.FINISHED || state == CatchEventStateEnum.TERMINATED;
  }

  @Override
  public boolean stateAllowsTerminate() {
    return state == CatchEventStateEnum.WAITING || state == CatchEventStateEnum.READY;
  }

  @Override
  public void terminate() {
    // Do nothing
    if (stateAllowsTerminate()) {
      state = CatchEventStateEnum.TERMINATED;
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
              escalationEventDefinition.getReferencedEscalation().name(),
              escalationEventDefinition.getReferencedEscalation().escalationCode()));
    } else {
      this.catchAllEscalations = true;
    }
  }

  public void addErrorSubscription(ErrorEventDefinition errorEventDefinition) {
    if (errorEventDefinition.getReferencedError() != null) {
      errorSubscriptions.add(
          new ErrorSubscription(
              errorEventDefinition.getReferencedError().name(),
              errorEventDefinition.getReferencedError().code()));
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
