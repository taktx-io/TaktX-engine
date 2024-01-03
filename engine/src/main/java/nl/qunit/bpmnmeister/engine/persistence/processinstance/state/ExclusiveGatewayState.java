package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class ExclusiveGatewayState extends BpmnElementState {
  @BsonCreator
  public ExclusiveGatewayState(@BsonProperty("state") StateEnum state) {
    super(state);
  }
}
