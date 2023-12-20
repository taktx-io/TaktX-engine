package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TaskState;

public record Task(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new TaskState(StateEnum.INIT, 0);
  }
}
