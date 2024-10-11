package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduledKey;

@Getter
public class InstanceResult {

  private final List<FLowNodeInstanceInfo> newFlowNodeInstanceInfos = new ArrayList<>();
  private final List<UUID> terminateInstances = new ArrayList<>();
  private final List<ExternalTaskInfo> externalTaskRequests = new ArrayList<>();
  private final List<NewStartCommand> newStartCommands = new ArrayList<>();
  private final List<UUID> newTerminateCommands = new ArrayList<>();
  private final List<ContinueFlowElementTrigger> continuations = new ArrayList<>();
  private final List<NewCorrelationSubscriptionMessageEventInfo>
      newCorrelationSubscriptionMessageEventInfos = new ArrayList<>();
  private final List<TerminateCorrelationSubscriptionMessageEventInfo>
      terminateCorrelationSubscriptionMessageEventInfos = new ArrayList<>();
  private final List<ScheduledContinuationInfo> scheduledContinuationInfos = new ArrayList<>();
  private final List<ScheduledKey> cancelSchedules = new ArrayList<>();
  private final List<EventSignal> events = new ArrayList<>();
  private final List<EventSignal> bubbleUpEvents = new ArrayList<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addNewFlowNodeInstance(FLowNodeInstanceInfo flowNodeInstanceInfo) {
    newFlowNodeInstanceInfos.add(flowNodeInstanceInfo);
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
    terminateInstances.addAll(toMerge.getTerminateInstances());
    events.addAll(toMerge.getEvents());
    bubbleUpEvents.addAll(toMerge.getBubbleUpEvents());
  }

  public void cancelSchedule(ScheduledKey scheduledKey) {
    cancelSchedules.add(scheduledKey);
  }

  public void addTerminateInstance(UUID terminateInstanceId) {
    this.terminateInstances.add(terminateInstanceId);
  }

  public void clearDirectTriggers() {
    this.events.clear();
    this.terminateInstances.clear();
    this.newFlowNodeInstanceInfos.clear();
  }

  public boolean hasDirectTriggers() {
    return !newFlowNodeInstanceInfos.isEmpty()
        || !terminateInstances.isEmpty()
        || !events.isEmpty();
  }

  public void addEvent(EventSignal event) {
    events.add(event);
  }

  public void bubbleUp(EventSignal event) {
    bubbleUpEvents.add(event);
  }
}
