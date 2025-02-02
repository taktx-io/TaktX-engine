package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.CallActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CallActivity extends Activity {

  private String calledElement;
  private boolean propagateAllParentVariables;
  private boolean propagateAllChildVariables;

  @Override
  public ActivityInstance<?> newActivityInstance(FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new CallActivityInstance(parentInstance, this, elementInstanceId);
  }
}
