package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.EndEventInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class EndEvent extends ThrowEvent {

  public EndEventInstance newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new EndEventInstance(parentInstance, this);
  }
}
