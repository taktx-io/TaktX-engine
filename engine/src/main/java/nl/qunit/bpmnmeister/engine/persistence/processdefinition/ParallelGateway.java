package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ParallelGatewayState;

public record ParallelGateway(String id, Set<String> inputFlows, Set<String> outputFlows)
    implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new ParallelGatewayState(new HashSet<>());
  }
}
