package nl.qunit.bpmnmeister.pd.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode extends FlowElement {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  private final Set<SequenceFlow> incomingSequenceFlows = new HashSet<>();
  private final Set<SequenceFlow> outGoingSequenceFlows = new HashSet<>();

  public FLowNodeInstance<?> createAndStoreNewInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    FLowNodeInstance<?> newInstance = newInstance(parentInstance, flowNodeInstances);
    flowNodeInstances.putInstance(newInstance);
    return newInstance;
  }

  public abstract FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances);
}
