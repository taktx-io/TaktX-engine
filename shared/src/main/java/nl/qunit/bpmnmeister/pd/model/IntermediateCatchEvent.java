package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent extends CatchEvent {

  @Override
  public FLowNodeInstance<?> newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new IntermediateCatchEventInstance(parentInstance, this);
  }

  public boolean hasLinkEventDefinition(String name) {
    return getLinkventDefinitions().stream().anyMatch(e -> e.getName().equals(name));
  }
}
