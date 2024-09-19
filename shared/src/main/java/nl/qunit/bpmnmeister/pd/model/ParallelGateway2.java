package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.GatewayInstance;
import nl.qunit.bpmnmeister.pi.instances.ParallelGatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ParallelGateway2 extends Gateway2 {

  @Override
  protected GatewayInstance newSpecificGatewayInstance(FLowNodeInstance<?> parentInstance) {
    return new ParallelGatewayInstance(parentInstance, this);
  }
}
