package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeStates2;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.IntermediateCatchEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class IntermediateCatchEvent2 extends CatchEvent2 {

  @Override
  public FLowNodeInstance newInstance(
      FLowNodeInstance parentInstance, FlowNodeStates2 flowNodeStates) {
    return new IntermediateCatchEventInstance(parentInstance, this);
  }

  public boolean hasLinkEventDefinition(String name) {
    return getLinkventDefinitions().stream().anyMatch(e -> e.getName().equals(name));
  }
}
