package com.flomaestro.engine.pd.model;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@SuperBuilder
public abstract class EventSignal {
  @Setter private FlowNodeInstance<?> sourceInstance;
  private final String name;

  public void selectParent() {
    if (sourceInstance.getParentInstance() != null) {
      sourceInstance = sourceInstance.getParentInstance();
    }
  }

  public abstract boolean bubbleUp();
}
