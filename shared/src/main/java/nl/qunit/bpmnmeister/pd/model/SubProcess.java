package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.SubProcessInstance;

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
