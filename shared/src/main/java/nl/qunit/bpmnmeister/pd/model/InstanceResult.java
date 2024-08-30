package nl.qunit.bpmnmeister.pd.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ExternalTaskInfo;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

public class InstanceResult {

  @Getter private final List<FLowNodeInstance> newFlowNodeInstances = new ArrayList<>();
  @Getter private final List<ExternalTaskInfo> externalTaskRequests = new ArrayList<>();

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

  public void merge(InstanceResult toMerge) {
    newFlowNodeInstances.addAll(toMerge.getNewFlowNodeInstances());
    externalTaskRequests.addAll(toMerge.getExternalTaskRequests());
  }
}
