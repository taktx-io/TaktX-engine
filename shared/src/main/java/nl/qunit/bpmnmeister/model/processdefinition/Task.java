package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.StateEnum;
import nl.qunit.bpmnmeister.model.processinstance.TaskState;

public record Task(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new TaskState(StateEnum.INIT, 0);
  }
}
