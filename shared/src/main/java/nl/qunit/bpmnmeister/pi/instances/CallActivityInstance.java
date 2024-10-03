package nl.qunit.bpmnmeister.pi.instances;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.CallActivity;

@NoArgsConstructor
@Setter
@Getter
public class CallActivityInstance extends ActivityInstance<CallActivity> {

  private UUID childProcessInstanceId;

  public CallActivityInstance(FLowNodeInstance<?> parentInstance, CallActivity flowNode) {
    super(parentInstance, flowNode);
  }
}
