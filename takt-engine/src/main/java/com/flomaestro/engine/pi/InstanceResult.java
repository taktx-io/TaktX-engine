package com.flomaestro.engine.pi;

import com.flomaestro.engine.pd.model.NewStartCommand;
import com.flomaestro.engine.pi.model.ExternalTaskInfo;
import com.flomaestro.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.engine.pi.model.ScheduledContinuationInfo;
import com.flomaestro.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import com.flomaestro.takt.dto.v_1_0_0.ContinueFlowElementTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.ScheduledKeyDTO;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import lombok.Getter;

@Getter
public class InstanceResult {

  private final Queue<InstanceUpdateDTO> processInstanceUpdates = new ArrayDeque<>();
  private final Queue<ExternalTaskInfo> externalTaskRequests = new ArrayDeque<>();
  private final Queue<NewStartCommand> newStartCommands = new ArrayDeque<>();
  private final Queue<UUID> newTerminateCommands = new ArrayDeque<>();
  private final Queue<ContinueFlowElementTriggerDTO> continuations = new ArrayDeque<>();
  private final Queue<NewCorrelationSubscriptionMessageEventInfo>
      newCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<TerminateCorrelationSubscriptionMessageEventInfo>
      terminateCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<ScheduledContinuationInfo> scheduledContinuationInfos = new ArrayDeque<>();
  private final Queue<ScheduledKeyDTO> cancelSchedules = new ArrayDeque<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addProcessInstanceUpdate(InstanceUpdateDTO processInstanceUpdate) {
    processInstanceUpdates.add(processInstanceUpdate);
  }

  public void addExternalTaskRequest(ExternalTaskInfo externalTaskInfo) {
    externalTaskRequests.add(externalTaskInfo);
  }

  public void addNewStartCommand(NewStartCommand newStartCommand) {
    newStartCommands.add(newStartCommand);
  }

  public void addContinuation(ContinueFlowElementTriggerDTO continueFlowElementTrigger) {
    continuations.add(continueFlowElementTrigger);
  }

  public void addTerminateCommand(UUID childProcessInstanceId) {
    newTerminateCommands.add(childProcessInstanceId);
  }

  public void addNewCorrelationSubcriptionMessageEvent(
      NewCorrelationSubscriptionMessageEventInfo messageEvent) {
    newCorrelationSubscriptionMessageEventInfos.add(messageEvent);
  }

  public void addTerminateCorrelationSubscriptionMessageEvent(
      TerminateCorrelationSubscriptionMessageEventInfo messageEvent) {
    terminateCorrelationSubscriptionMessageEventInfos.add(messageEvent);
  }

  public void addNewScheduledContinuation(ScheduledContinuationInfo scheduledContinuationInfo) {
    scheduledContinuationInfos.add(scheduledContinuationInfo);
  }

  public void cancelSchedule(ScheduledKeyDTO scheduledKey) {
    cancelSchedules.add(scheduledKey);
  }
}
