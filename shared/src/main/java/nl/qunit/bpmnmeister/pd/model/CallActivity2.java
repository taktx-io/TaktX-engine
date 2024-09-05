package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.CallActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CallActivity2 extends Activity2 {

  private String calledElement;
  private boolean propagateAllParentVariables;
  private boolean propagateAllChildVariables;

  @Override
  public FLowNodeInstance newInstance(FLowNodeInstance parentInstance) {
    return new CallActivityInstance(getId(), parentInstance);
  }
}
