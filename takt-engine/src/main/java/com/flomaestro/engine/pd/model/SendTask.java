package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.SendTaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SendTask extends ExternalTask {

  @Override
  public ActivityInstance<?> newActivityInstance(FlowNodeInstance<?> parentInstance) {
    return new SendTaskInstance(parentInstance, this);
  }
}
