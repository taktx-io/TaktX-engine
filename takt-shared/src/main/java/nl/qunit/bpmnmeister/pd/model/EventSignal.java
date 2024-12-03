package nl.qunit.bpmnmeister.pd.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public abstract class EventSignal {
  @Setter private FLowNodeInstance<?> sourceInstance;
  private final String name;

  public void selectParent() {
    if (sourceInstance.getParentInstance() != null) {
      sourceInstance = sourceInstance.getParentInstance();
    }
  }

  public abstract boolean bubbleUp();
}
