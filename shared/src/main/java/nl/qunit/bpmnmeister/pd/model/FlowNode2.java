package nl.qunit.bpmnmeister.pd.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode2 extends FlowElement2 {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  private final Set<SequenceFlow2> incomingSequenceFlows = new HashSet<>();
  private final Set<SequenceFlow2> outGoingSequenceFlows = new HashSet<>();

  public FLowNodeInstance<?> createAndStoreNewInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeStates2 flowNodeStates) {
    FLowNodeInstance<?> newInstance = newInstance(parentInstance, flowNodeStates);
    flowNodeStates.putInstance(newInstance);
    return newInstance;
  }

  public abstract FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeStates2 flowNodeStates);
}
