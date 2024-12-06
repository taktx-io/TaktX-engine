package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.CallActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class CallActivity extends Activity {

  private String calledElement;
  private boolean propagateAllParentVariables;
  private boolean propagateAllChildVariables;

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new CallActivityInstance(parentInstance, this);
  }
}
