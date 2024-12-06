package nl.qunit.bpmnmeister.engine.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.pi.model.ActivityInstance;
import nl.qunit.bpmnmeister.engine.pi.model.FLowNodeInstance;
import nl.qunit.bpmnmeister.engine.pi.model.TaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class Task extends Activity {

  @Override
  public ActivityInstance<?> newActivityInstance(FLowNodeInstance<?> parentInstance) {
    return new TaskInstance(parentInstance, this);
  }
}
