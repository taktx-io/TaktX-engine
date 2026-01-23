package io.taktx.engine.pi.model.subscriptions;

import io.taktx.dto.InstanceScheduleKeyDTO;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.TimerEventDefinition;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.ScheduledEventInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.TimerEventSignal;
import io.taktx.engine.pi.model.VariableScope;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimerSubscription extends Subscription {
  private InstanceScheduleKeyDTO scheduledKey;

  public TimerSubscription() {
    setOrder(5);
  }

  private TimerSubscription(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      String flowNodeId,
      SubScriptionType subScriptionType,
      TimerEventDefinition timerEventDefinition,
      VariableScope variableScope) {
    super(0, subScriptionType, flowNodeId);

    TimerEventSignal timerEventSignal = new TimerEventSignal(flowNodeInstance, flowNodeId);

    // ScheduleKey will be set when actually scheduling the timer
    processInstanceProcessingContext
        .getInstanceResult()
        .addNewScheduledEvent(
            new ScheduledEventInfo(this, timerEventSignal, timerEventDefinition, variableScope));
  }

  public static TimerSubscription starting(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      FlowNode flowNode,
      TimerEventDefinition timerEventDefinition,
      VariableScope variableScope) {
    return new TimerSubscription(
        processInstanceProcessingContext,
        flowNodeInstance,
        flowNode.getId(),
        SubScriptionType.STARTING,
        timerEventDefinition,
        variableScope);
  }

  public static TimerSubscription continuing(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      TimerEventDefinition timerEventDefinition,
      VariableScope variableScope) {
    return new TimerSubscription(
        processInstanceProcessingContext,
        flowNodeInstance,
        null,
        SubScriptionType.CONTINUING,
        timerEventDefinition,
        variableScope);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    if (!(event instanceof TimerEventSignal timerEventSignal)) {
      return false;
    }
    List<Long> path =
        timerEventSignal.getCurrentInstance() != null
            ? timerEventSignal.getCurrentInstance().createKeyPath()
            : List.of();
    if (!path.equals(scheduledKey.getElementInstanceIdPath())) {
      return false;
    }
    if (scheduledKey.getElementId() != null) {
      return scheduledKey.getElementId().equals(timerEventSignal.getElementId());
    }
    return true;
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    processInstanceProcessingContext.getInstanceResult().cancelSchedule(scheduledKey);
  }
}
