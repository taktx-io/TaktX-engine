package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.CallActivity;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class CallActivityInstance extends ActivityInstance<CallActivity> {

  private UUID childProcessInstanceId;

  public CallActivityInstance(FlowNodeInstance<?> parentInstance, CallActivity flowNode) {
    super(parentInstance, flowNode);
  }
}
