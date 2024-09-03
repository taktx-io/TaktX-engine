package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ExternalTaskInstance extends ActivityInstance {
  private int attempt;

  public ExternalTaskInstance(String flowNode, FLowNodeInstance parentInstance) {
    super(flowNode, parentInstance);
  }

  public int increaseAttempt() {
    return ++attempt;
  }
}
