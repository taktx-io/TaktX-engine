package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.GatewayInstance;
import com.flomaestro.engine.pi.model.ParallelGatewayInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ParallelGateway extends Gateway {

  @Override
  protected GatewayInstance<?> newSpecificGatewayInstance(FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new ParallelGatewayInstance(parentInstance, this, elementInstanceId);
  }
}
