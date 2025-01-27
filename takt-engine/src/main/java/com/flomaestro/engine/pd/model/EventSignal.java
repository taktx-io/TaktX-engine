package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import java.util.LinkedList;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class EventSignal {
  private LinkedList<FlowNodeInstance<?>> pathToSource = new LinkedList<>();
  private final String name;

  public EventSignal(FlowNodeInstance<?> fLowNodeInstance, String name) {
    pathToSource.addFirst(fLowNodeInstance);
    this.name = name;
  }

  public FlowNodeInstance<?> bubbleUp() {
    FlowNodeInstance<?> parentInstance = pathToSource.getLast().getParentInstance();
    if (parentInstance != null) {
      pathToSource.addFirst(parentInstance);
    }
    return parentInstance;
  }

  public FlowNodeInstance<?> getCurrentInstance() {
    return pathToSource.getFirst();
  }
}
