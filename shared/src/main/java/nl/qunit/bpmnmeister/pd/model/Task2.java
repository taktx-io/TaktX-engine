package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.TaskInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class Task2 extends Activity2 {

  @Override
  public TaskInstance newInstance(FLowNodeInstance parentInstance) {
    return new TaskInstance(getId(), parentInstance);
  }
}
