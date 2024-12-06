package nl.qunit.bpmnmeister.engine.pd.model;

import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FlowNodeInstances;
import nl.qunit.bpmnmeister.engine.pi.model.GatewayInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Gateway extends FlowNode {
  private String defaultFlow;
  @Setter private SequenceFlow defaultSequenceFlow;

  @Override
  public FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    Optional<GatewayInstance> optGatewayInstance =
        flowNodeInstances.getInstances().values().stream()
            .filter(GatewayInstance.class::isInstance)
            .map(GatewayInstance.class::cast)
            .filter(instance -> instance.getFlowNode().getId().equals(getId()))
            .findFirst();
    return optGatewayInstance.orElse(newSpecificGatewayInstance(parentInstance));
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      FLowNodeInstance<?> parentInstance);
}
