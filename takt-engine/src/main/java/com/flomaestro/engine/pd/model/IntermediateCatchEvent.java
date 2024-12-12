package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.IntermediateCatchEventInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent extends CatchEvent {

  @Override
  public FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new IntermediateCatchEventInstance(parentInstance, this);
  }

  public boolean hasLinkEventDefinition(String name) {
    return getLinkventDefinitions().stream().anyMatch(e -> e.getName().equals(name));
  }
}
