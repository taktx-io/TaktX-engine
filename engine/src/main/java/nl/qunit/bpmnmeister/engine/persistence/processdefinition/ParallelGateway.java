package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ParallelGatewayState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode(callSuper = true)
public class ParallelGateway extends BpmnElement {
  private Set<String> inputFlows;

  public ParallelGateway() {
    super();
  }

  public ParallelGateway(String id, Set<String> outputFlows, Set<String> inputFlows) {
    super(id, outputFlows);
    this.inputFlows = inputFlows;
  }

  @Override
  public BpmnElementState createState() {
    return new ParallelGatewayState();
  }
}
