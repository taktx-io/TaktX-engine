package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.ServiceTaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class ServiceTask extends ExternalTask {

  @Override
  public ActivityInstance<?> newActivityInstance(FlowNodeInstance<?> parentInstance) {
    return new ServiceTaskInstance(parentInstance, this);
  }
}
