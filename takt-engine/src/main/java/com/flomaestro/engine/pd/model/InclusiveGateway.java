package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.InclusiveGatewayInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class InclusiveGateway extends Gateway {

  @Override
  protected InclusiveGatewayInstance newSpecificGatewayInstance(
      FlowNodeInstance<?> parentInstance) {
    return new InclusiveGatewayInstance(parentInstance, this);
  }
}
