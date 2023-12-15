package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.StartEventState;

public record StartEvent(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new StartEventState();
  }
}
