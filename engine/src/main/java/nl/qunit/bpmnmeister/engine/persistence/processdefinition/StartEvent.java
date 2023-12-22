package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Data
@EqualsAndHashCode(callSuper = true)
@BsonDiscriminator
public class StartEvent extends BpmnElement {
  public StartEvent() {
    super();
  }

  public StartEvent(String id, Set<String> outputFlows) {
    super(id, outputFlows);
  }

  @Override
  public BpmnElementState createState() {
    return new StartEventState();
  }
}
