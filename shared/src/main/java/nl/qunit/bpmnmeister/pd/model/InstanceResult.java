package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
public class InstanceResult {

  private final List<FLowNodeInstanceInfo> newFlowNodeInstanceInfos = new ArrayList<>();
  private final List<ExternalTaskInfo> externalTaskRequests = new ArrayList<>();
  private final List<NewStartCommand> newStartCommands = new ArrayList<>();
  private final List<UUID> newTerminateCommands = new ArrayList<>();
  private final List<ContinueFlowElementTrigger2> continuations = new ArrayList<>();
  private final List<NewCorrelationSubscriptionMessageEventInfo>
      newCorrelationSubscriptionMessageEventInfos = new ArrayList<>();
  private final List<TerminateCorrelationSubscriptionMessageEventInfo>
      terminateCorrelationSubscriptionMessageEventInfos = new ArrayList<>();
  private final List<ScheduledContinuationInfo> scheduledContinuationInfos = new ArrayList<>();
  private final List<ScheduledKey> cancelSchedules = new ArrayList<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addNewFlowNodeInstance(FLowNodeInstanceInfo flowNodeInstanceInfo) {
    newFlowNodeInstanceInfos.add(flowNodeInstanceInfo);
  }

  public boolean hasNewFlowNodeInstances() {
    return !newFlowNodeInstanceInfos.isEmpty();
  }

  public void addExternalTaskRequest(ExternalTaskInfo externalTaskInfo) {
    externalTaskRequests.add(externalTaskInfo);
  }

  public void addNewStartCommand(NewStartCommand newStartCommand) {
    newStartCommands.add(newStartCommand);
  }

  public void addContinuation(ContinueFlowElementTrigger2 continueFlowElementTrigger2) {
    continuations.add(continueFlowElementTrigger2);
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

  public void merge(InstanceResult toMerge) {
    newFlowNodeInstanceInfos.addAll(toMerge.getNewFlowNodeInstanceInfos());
    externalTaskRequests.addAll(toMerge.getExternalTaskRequests());
    newStartCommands.addAll(toMerge.getNewStartCommands());
    continuations.addAll(toMerge.getContinuations());
    newTerminateCommands.addAll(toMerge.getNewTerminateCommands());
    newCorrelationSubscriptionMessageEventInfos.addAll(
        toMerge.getNewCorrelationSubscriptionMessageEventInfos());
    terminateCorrelationSubscriptionMessageEventInfos.addAll(
        toMerge.getTerminateCorrelationSubscriptionMessageEventInfos());
    scheduledContinuationInfos.addAll(toMerge.getScheduledContinuationInfos());
    cancelSchedules.addAll(toMerge.getCancelSchedules());
  }

  public void cancelSchedule(ScheduledKey scheduledKey) {
    cancelSchedules.add(scheduledKey);
  }
}
