package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.CallActivity2;

@NoArgsConstructor
@Setter
@Getter
public class CallActivityInstance extends ActivityInstance<CallActivity2> {

  private UUID childProcessInstanceId;

  public CallActivityInstance(FLowNodeInstance parentInstance, CallActivity2 flowNode) {
    super(parentInstance, flowNode);
  }
}
