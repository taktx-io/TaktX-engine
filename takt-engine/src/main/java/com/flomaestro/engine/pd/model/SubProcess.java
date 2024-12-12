package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.SubProcessInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SubProcess extends Activity implements WIthChildElements {
  private FlowElements elements;

  @Override
  public ActivityInstance<?> newActivityInstance(FlowNodeInstance<?> parentInstance) {
    return new SubProcessInstance(parentInstance, this);
  }
}
