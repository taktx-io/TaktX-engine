package nl.qunit.bpmnmeister.pd.model;

import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.GatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Gateway2 extends FlowNode2 {
  private String defaultFlow;
  @Setter private SequenceFlow2 defaultSequenceFlow;

  @Override
  public FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeStates2 flowNodeStates) {
    Optional<GatewayInstance> optGatewayInstance =
        flowNodeStates.getFlowNodeInstances().values().stream()
            .filter(GatewayInstance.class::isInstance)
            .map(GatewayInstance.class::cast)
            .filter(instance -> instance.getFlowNode().getId().equals(getId()))
            .findFirst();
    return optGatewayInstance.orElse(newSpecificGatewayInstance(parentInstance));
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      FLowNodeInstance<?> parentInstance);
}
