package nl.qunit.bpmnmeister.pi.instances;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.SendTask;

@NoArgsConstructor
public class SendTaskInstance extends ExternalTaskInstance<SendTask> {

  public SendTaskInstance(FLowNodeInstance<?> parentInstance, SendTask flowNode) {
    super(parentInstance, flowNode);
  }
}
