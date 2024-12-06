package nl.qunit.bpmnmeister.engine.pi.model;

import lombok.NoArgsConstructor;
import nl.qunit.bpmnmeister.engine.pd.model.SendTask;

@NoArgsConstructor
public class SendTaskInstance extends ExternalTaskInstance<SendTask> {

  public SendTaskInstance(FLowNodeInstance<?> parentInstance, SendTask flowNode) {
    super(parentInstance, flowNode);
  }
}
