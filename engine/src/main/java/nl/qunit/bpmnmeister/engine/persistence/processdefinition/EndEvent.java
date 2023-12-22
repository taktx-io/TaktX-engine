package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StartEventState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode(callSuper = true)
public class EndEvent extends BpmnElement {
  public EndEvent() {}

  public EndEvent(String id, Set<String> outputFlows) {
    super(id, outputFlows);
  }

  @Override
  public BpmnElementState createState() {
    return new StartEventState(StateEnum.INIT);
  }
}
