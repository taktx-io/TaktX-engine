package nl.qunit.bpmnmeister.engine.pi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.EventSignal;
import nl.qunit.bpmnmeister.pd.model.FLowNodeInstanceInfo;
import nl.qunit.bpmnmeister.pi.ProcessInstance;

public class DirectInstanceResult {
  private final Queue<FLowNodeInstanceInfo> newFlowNodeInstanceInfos = new ArrayDeque<>();
  private final List<String> sequenceFlows = new ArrayList<>();
  private final Queue<UUID> terminateInstances = new ArrayDeque<>();
  private final Queue<EventSignal> events = new ArrayDeque<>();

  private DirectInstanceResult() {}

  public static DirectInstanceResult empty() {
    return new DirectInstanceResult();
  }

  public void addNewFlowNodeInstance(
      ProcessInstance processInstance, FLowNodeInstanceInfo flowNodeInstanceInfo) {
    if (!flowNodeInstanceInfo.inputSequenceFlowId().equals(Constants.NONE)) {
      if (sequenceFlows.contains(flowNodeInstanceInfo.inputSequenceFlowId())) {
        throw new ProcessInstanceException(
            processInstance,
            flowNodeInstanceInfo.flowNodeInstance(),
            "Straight through processing loop detected for sequenceflow "
                + flowNodeInstanceInfo.inputSequenceFlowId()
                + " in: "
                + sequenceFlows);
      }
      sequenceFlows.add(flowNodeInstanceInfo.inputSequenceFlowId());
    }
    newFlowNodeInstanceInfos.add(flowNodeInstanceInfo);
  }

  public FLowNodeInstanceInfo pollNewFlowNodeInstance() {
    return newFlowNodeInstanceInfos.poll();
  }

  public UUID pollTerminateInstance() {
    return terminateInstances.poll();
  }

  public EventSignal pollEvent() {
    return events.poll();
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

  public boolean eventsEmpty() {
    return events.isEmpty();
  }

  public boolean terminateInstancesIsEmpty() {
    return terminateInstances.isEmpty();
  }

  public boolean newFlowNodeInstancesIsEmpty() {
    return newFlowNodeInstanceInfos.isEmpty();
  }
}
