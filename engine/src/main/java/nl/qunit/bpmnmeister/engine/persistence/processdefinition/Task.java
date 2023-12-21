package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TaskState;

@Data
@EqualsAndHashCode(callSuper = true)
public class Task extends BpmnElement {

  public Task() {}

  public Task(String id, Set<String> outputFlows) {
    super(id, outputFlows);
  }

  @Override
  public BpmnElementState createState() {
    return new TaskState(StateEnum.INIT, 0);
  }
}
