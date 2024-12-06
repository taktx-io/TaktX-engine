package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.SubProcessInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SubProcess extends Activity implements WIthChildElements {
  private FlowElements elements;

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new SubProcessInstance(parentInstance, this);
  }
}
