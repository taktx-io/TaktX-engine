package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;

@NoArgsConstructor
public class StartEventInstance extends CatchEventInstance<StartEvent2> {

  public StartEventInstance(FLowNodeInstance<?> parentInstance, StartEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}
