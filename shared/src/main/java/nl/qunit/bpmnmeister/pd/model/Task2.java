package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.TaskInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public class Task2 extends Activity2 {

  @Override
  public TaskInstance newInstance() {
    return new TaskInstance(getId());
  }
}
