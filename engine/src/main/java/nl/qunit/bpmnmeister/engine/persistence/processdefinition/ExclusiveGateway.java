package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ExclusiveGatewayState;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExclusiveGateway extends BpmnElement {
  public ExclusiveGateway() {
    super();
  }

  public ExclusiveGateway(String id, Set<String> outputFlows) {

    super(id, outputFlows);
  }

  @Override
  public BpmnElementState createState() {
    return new ExclusiveGatewayState();
  }
}
