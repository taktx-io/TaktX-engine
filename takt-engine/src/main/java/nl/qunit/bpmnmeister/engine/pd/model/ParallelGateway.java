package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.GatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.ParallelGatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ParallelGateway extends Gateway {

  @Override
  protected GatewayInstance<?> newSpecificGatewayInstance(FLowNodeInstance<?> parentInstance) {
    return new ParallelGatewayInstance(parentInstance, this);
  }
}
