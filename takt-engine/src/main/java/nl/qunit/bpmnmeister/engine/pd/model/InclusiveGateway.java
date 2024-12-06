package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.InclusiveGatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class InclusiveGateway extends Gateway {

  @Override
  protected InclusiveGatewayInstance newSpecificGatewayInstance(
      FLowNodeInstance<?> parentInstance) {
    return new InclusiveGatewayInstance(parentInstance, this);
  }
}
