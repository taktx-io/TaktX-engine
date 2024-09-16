package nl.qunit.bpmnmeister.pi.instances;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.ReceiveTask2;

@NoArgsConstructor
@Getter
@Setter
public class ReceiveTaskInstance extends ActivityInstance<ReceiveTask2> {

  private String correlationKey;

  public ReceiveTaskInstance(FLowNodeInstance<?> parentInstance, ReceiveTask2 flowNode) {
    super(parentInstance, flowNode);
  }
}
