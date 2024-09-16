package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.ExternalTask2;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance<N extends ExternalTask2> extends ActivityInstance<N> {
  private int attempt;

  public ExternalTaskInstance(FLowNodeInstance parentInstance, N flowNode) {
    super(parentInstance, flowNode);
  }

  public int increaseAttempt() {
    return ++attempt;
  }
}
