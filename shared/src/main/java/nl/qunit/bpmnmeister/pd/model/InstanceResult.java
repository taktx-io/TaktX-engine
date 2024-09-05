package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ContinueFlowElementTrigger2;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.NewStartCommand;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
public class InstanceResult {

  private final List<FLowNodeInstance> newFlowNodeInstances = new ArrayList<>();
  private final List<ExternalTaskInfo> externalTaskRequests = new ArrayList<>();
  private final List<NewStartCommand> newStartCommands = new ArrayList<>();
  private final List<ContinueFlowElementTrigger2> continuations = new ArrayList<>();

  public static InstanceResult empty() {
    return new InstanceResult();
  }

  public void addNewFlowNodeInstance(FLowNodeInstance flowNodeInstance) {
    newFlowNodeInstances.add(flowNodeInstance);
  }

  public boolean hasNewFlowNodeInstances() {
    return !newFlowNodeInstances.isEmpty();
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

  public void merge(InstanceResult toMerge) {
    newFlowNodeInstances.addAll(toMerge.getNewFlowNodeInstances());
    externalTaskRequests.addAll(toMerge.getExternalTaskRequests());
    newStartCommands.addAll(toMerge.getNewStartCommands());
    continuations.addAll(toMerge.getContinuations());
  }
}
