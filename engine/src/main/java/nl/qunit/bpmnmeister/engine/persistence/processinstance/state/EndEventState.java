package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class EndEventState extends BpmnElementState {
  @BsonCreator
  public EndEventState(@BsonProperty("state") StateEnum state) {
    super(state);
  }
}
