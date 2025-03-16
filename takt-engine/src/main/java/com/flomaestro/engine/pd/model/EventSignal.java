package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import java.util.LinkedList;
import lombok.Getter;

@Getter
public abstract class EventSignal {
  private final LinkedList<FlowNodeInstance<?>> pathToSource = new LinkedList<>();
  private final String name;

  protected EventSignal(FlowNodeInstance<?> fLowNodeInstance, String name) {
    pathToSource.addFirst(fLowNodeInstance);
    this.name = name;
  }

  public void bubbleUp() {
    FlowNodeInstance<?> parentInstance = pathToSource.getLast().getParentInstance();
    if (parentInstance != null) {
      pathToSource.addFirst(parentInstance);
    }
  }

  public FlowNodeInstance<?> getCurrentInstance() {
    return pathToSource.getFirst();
  }
}
