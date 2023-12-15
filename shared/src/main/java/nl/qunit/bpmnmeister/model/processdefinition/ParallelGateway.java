package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.ParallelGatewayState;

public record ParallelGateway(String id, Set<String> inputFlows, Set<String> outputFlows)
    implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new ParallelGatewayState(new HashSet<>());
  }
}
