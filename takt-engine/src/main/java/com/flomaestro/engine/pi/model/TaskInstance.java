package com.flomaestro.engine.pi.model;

import com.flomaestro.engine.pd.model.Task;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class TaskInstance extends ActivityInstance<Task> {
  public TaskInstance(FlowNodeInstance<?> parentInstance, Task flowNode) {
    super(parentInstance, flowNode);
  }
}
