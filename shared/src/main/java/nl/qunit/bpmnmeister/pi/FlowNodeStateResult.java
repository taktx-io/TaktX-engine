package nl.qunit.bpmnmeister.pi;

import java.util.HashSet;
import java.util.Set;

public class FlowNodeStateResult {
  private final Set<String> activatedSequenceFlowIds = new HashSet<>();

  public void addOutgoingSequenceFlowIds(Set<String> outgoing) {
    activatedSequenceFlowIds.addAll(outgoing);
  }

  public Set<String> getActivatedSequenceFlowIds() {
    return activatedSequenceFlowIds;
  }
}
