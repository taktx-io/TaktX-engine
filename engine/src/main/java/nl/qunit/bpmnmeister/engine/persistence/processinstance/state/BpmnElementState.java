package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Getter
@SuperBuilder
public abstract class BpmnElementState {
  StateEnum state;

  protected BpmnElementState(StateEnum state) {
    this.state = state;
  }
}
