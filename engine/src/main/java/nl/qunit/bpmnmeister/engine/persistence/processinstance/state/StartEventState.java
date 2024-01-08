package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class StartEventState extends BpmnElementState {
  @BsonCreator
  public StartEventState(@BsonProperty("state") StateEnum state) {
    super(state);
  }
}
