package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.ActivityInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;
import nl.qunit.bpmnmeister.pi.instances.TaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class Task extends Activity {

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new TaskInstance(parentInstance, this);
  }
}
