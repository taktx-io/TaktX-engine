package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@BsonDiscriminator
@Getter
public class NoopState extends BpmnElementState {
  protected NoopState(BpmnElementStateBuilder<?, ?> b) {
    super(b);
  }
}
