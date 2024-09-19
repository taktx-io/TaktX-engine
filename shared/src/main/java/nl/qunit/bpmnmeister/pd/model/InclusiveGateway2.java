package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.InclusiveGatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class InclusiveGateway2 extends Gateway2 {

  private String defaultFlow;

  @Override
  protected InclusiveGatewayInstance newSpecificGatewayInstance(
      FLowNodeInstance<?> parentInstance) {
    return new InclusiveGatewayInstance(parentInstance, this);
  }
}
