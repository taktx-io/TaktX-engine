package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstances;
import com.flomaestro.engine.pi.model.GatewayInstance;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Gateway extends FlowNode {
  private String defaultFlow;
  @Setter private SequenceFlow defaultSequenceFlow;

  @Override
  public FlowNodeInstance<?> newInstance(
      FlowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    Optional<GatewayInstance> optGatewayInstance =
        flowNodeInstances.getInstances().values().stream()
            .filter(GatewayInstance.class::isInstance)
            .map(GatewayInstance.class::cast)
            .filter(instance -> instance.getFlowNode().getId().equals(getId()))
            .findFirst();
    return optGatewayInstance.orElse(newSpecificGatewayInstance(parentInstance, flowNodeInstances.nextElementInstanceId()));
  }

  protected abstract GatewayInstance<?> newSpecificGatewayInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId);
}
