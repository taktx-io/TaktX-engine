package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.SendTask2;

@NoArgsConstructor
public class SendTaskInstance extends ExternalTaskInstance<SendTask2> {

  public SendTaskInstance(FLowNodeInstance<?> parentInstance, SendTask2 flowNode) {
    super(parentInstance, flowNode);
  }
}
