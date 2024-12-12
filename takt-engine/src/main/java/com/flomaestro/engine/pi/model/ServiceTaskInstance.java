package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.ServiceTask;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServiceTaskInstance extends ExternalTaskInstance<ServiceTask> {

  public ServiceTaskInstance(FlowNodeInstance<?> parentInstance, ServiceTask flowNode) {
    super(parentInstance, flowNode);
  }
}
