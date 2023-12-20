package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ExclusiveGatewayState;

public record ExclusiveGateway(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new ExclusiveGatewayState();
  }
}
