package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.ActivityInstance;
import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.TaskInstance;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class Task extends Activity {

  @Override
  public ActivityInstance<?> newActivityInstance(
      FlowNodeInstance<?> parentInstance, long elementInstanceId) {
    return new TaskInstance(parentInstance, this, elementInstanceId);
  }
}
