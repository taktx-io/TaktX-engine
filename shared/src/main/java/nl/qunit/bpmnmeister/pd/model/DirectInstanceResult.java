package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import lombok.Getter;

@Getter
public class DirectInstanceResult {
  private final Queue<FLowNodeInstanceInfo> newFlowNodeInstanceInfos = new ArrayDeque<>();
  private final Queue<UUID> terminateInstances = new ArrayDeque<>();
  private final Queue<EventSignal> events = new ArrayDeque<>();

  public static DirectInstanceResult empty() {
    return new DirectInstanceResult();
  }

  public void addNewFlowNodeInstance(FLowNodeInstanceInfo flowNodeInstanceInfo) {
    newFlowNodeInstanceInfos.add(flowNodeInstanceInfo);
  }

  public void addTerminateInstance(UUID terminateInstanceId) {
    this.terminateInstances.add(terminateInstanceId);
  }

  public boolean hasDirectTriggers() {
    return !newFlowNodeInstanceInfos.isEmpty()
        || !terminateInstances.isEmpty()
        || !events.isEmpty();
  }

  public void addEvent(EventSignal event) {
    events.add(event);
  }

  public void merge(DirectInstanceResult subDirectInstanceResult) {
    newFlowNodeInstanceInfos.addAll(subDirectInstanceResult.newFlowNodeInstanceInfos);
    terminateInstances.addAll(subDirectInstanceResult.terminateInstances);
    events.addAll(subDirectInstanceResult.events);
  }
}
