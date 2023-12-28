package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@Getter
@BsonDiscriminator
public abstract class FlowElement extends BaseElement {
  protected FlowElement(String id) {
    super(id);
  }

  public abstract TriggerResult trigger(Trigger trigger, BpmnElementState oldState);
}
