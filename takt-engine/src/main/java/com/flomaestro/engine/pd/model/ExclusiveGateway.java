package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ExclusiveGatewayInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ExclusiveGateway extends Gateway {

  @Override
  protected ExclusiveGatewayInstance newSpecificGatewayInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new ExclusiveGatewayInstance(parentInstance, this, elementInstanceId);
  }
}
