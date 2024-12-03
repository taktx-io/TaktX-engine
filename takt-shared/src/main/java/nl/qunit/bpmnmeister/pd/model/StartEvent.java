package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.FlowNodeInstances;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.StartEventInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class StartEvent extends CatchEvent {

  public StartEventInstance newInstance(
      FLowNodeInstance<?> parentInstance, FlowNodeInstances flowNodeInstances) {
    return new StartEventInstance(parentInstance, this);
  }
}
