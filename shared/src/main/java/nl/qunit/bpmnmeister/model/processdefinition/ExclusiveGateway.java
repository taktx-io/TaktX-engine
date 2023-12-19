package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.ExclusiveGatewayState;

public record ExclusiveGateway(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new ExclusiveGatewayState();
  }
}
