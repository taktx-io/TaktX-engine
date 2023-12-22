package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode
public class BpmnElementState {
  StateEnum state;
  public BpmnElementState() {
  }
  public BpmnElementState(StateEnum state) {
    this.state = state;
  }

  public TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement) {
    throw new IllegalStateException("Not implemented");
  }
}
