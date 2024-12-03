package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.ExternalTask;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance<N extends ExternalTask> extends ActivityInstance<N> {
  private int attempt;

  public ExternalTaskInstance(FLowNodeInstance<?> parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public int increaseAttempt() {
    return ++attempt;
  }
}
