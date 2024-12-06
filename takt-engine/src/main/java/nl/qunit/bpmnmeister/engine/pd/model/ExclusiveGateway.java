package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.ExclusiveGatewayInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ExclusiveGateway extends Gateway {

  @Override
  protected ExclusiveGatewayInstance newSpecificGatewayInstance(
      FLowNodeInstance<?> parentInstance) {
    return new ExclusiveGatewayInstance(parentInstance, this);
  }
}
