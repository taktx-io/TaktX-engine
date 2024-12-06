package nl.qunit.bpmnmeister.engine.pi;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.engine.pd.model.NewStartCommand;
import nl.qunit.bpmnmeister.engine.pi.model.ExternalTaskInfo;
import nl.qunit.bpmnmeister.engine.pi.model.NewCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.engine.pi.model.ScheduledContinuationInfo;
import nl.qunit.bpmnmeister.engine.pi.model.TerminateCorrelationSubscriptionMessageEventInfo;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.InstanceUpdate;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
public class InstanceResult {
  private final Queue<InstanceUpdate> processInstanceUpdates = new ArrayDeque<>();
  private final Queue<ExternalTaskInfo> externalTaskRequests = new ArrayDeque<>();
  private final Queue<NewStartCommand> newStartCommands = new ArrayDeque<>();
  private final Queue<UUID> newTerminateCommands = new ArrayDeque<>();
  private final Queue<ContinueFlowElementTrigger> continuations = new ArrayDeque<>();
  private final Queue<NewCorrelationSubscriptionMessageEventInfo>
      newCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<TerminateCorrelationSubscriptionMessageEventInfo>
      terminateCorrelationSubscriptionMessageEventInfos = new ArrayDeque<>();
  private final Queue<ScheduledContinuationInfo> scheduledContinuationInfos = new ArrayDeque<>();
  private final Queue<ScheduledKey> cancelSchedules = new ArrayDeque<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addProcessInstanceUpdate(InstanceUpdate processInstanceUpdate) {
    processInstanceUpdates.add(processInstanceUpdate);
  }

  public void addExternalTaskRequest(ExternalTaskInfo externalTaskInfo) {
    externalTaskRequests.add(externalTaskInfo);
  }

  public void addNewStartCommand(NewStartCommand newStartCommand) {
    newStartCommands.add(newStartCommand);
  }

  public void addContinuation(ContinueFlowElementTrigger continueFlowElementTrigger) {
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

  public void cancelSchedule(ScheduledKey scheduledKey) {
    cancelSchedules.add(scheduledKey);
  }
}
