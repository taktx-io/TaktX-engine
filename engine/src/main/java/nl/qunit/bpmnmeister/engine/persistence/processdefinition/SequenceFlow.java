package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.Trigger;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.TriggerResult;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

@SuperBuilder
@BsonDiscriminator
@Getter
public class SequenceFlow extends FlowElement {
  String source;
  String target;
  String condition;

  @BsonCreator
  public SequenceFlow(
      @BsonId String id,
      @BsonProperty("source") String source,
      @BsonProperty("target") String target,
      @BsonProperty("condition") String condition) {
    super(id);
    this.source = source;
    this.target = target;
    this.condition = condition;
  }

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElementState oldState) {
    return null;
  }
}
