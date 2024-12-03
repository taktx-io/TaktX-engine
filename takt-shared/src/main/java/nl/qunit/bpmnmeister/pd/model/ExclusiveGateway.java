package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ExclusiveGatewayInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

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
