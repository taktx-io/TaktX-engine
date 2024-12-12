package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode extends FlowElement {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  private final Set<SequenceFlow> incomingSequenceFlows = new HashSet<>();
  private final Set<SequenceFlow> outGoingSequenceFlows = new HashSet<>();

  public FlowNodeInstance<?> createAndStoreNewInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    FlowNodeInstance<?> newInstance = newInstance(parentInstance, flowNodeInstances);
    flowNodeInstances.putInstance(newInstance);
    return newInstance;
  }

  public abstract FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances);
}
