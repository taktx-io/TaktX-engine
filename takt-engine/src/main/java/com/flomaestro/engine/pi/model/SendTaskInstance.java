package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.SendTask;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SendTaskInstance extends ExternalTaskInstance<SendTask> {

  public SendTaskInstance(FlowNodeInstance<?> parentInstance, SendTask flowNode) {
    super(parentInstance, flowNode);
  }
}
