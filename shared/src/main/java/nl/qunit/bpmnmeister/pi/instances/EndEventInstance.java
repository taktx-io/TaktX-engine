package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.EndEvent2;

@NoArgsConstructor
public class EndEventInstance extends ThrowEventInstance<EndEvent2> {

  public EndEventInstance(FLowNodeInstance<?> parentInstance, EndEvent2 flowNode) {
    super(parentInstance, flowNode);
  }
}
