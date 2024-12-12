package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.IntermediateCatchEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class IntermediateCatchEventInstance extends CatchEventInstance<IntermediateCatchEvent> {

  public IntermediateCatchEventInstance(
      FlowNodeInstance<?> parentInstance, IntermediateCatchEvent flowNode) {
    super(parentInstance, flowNode);
  }
}
