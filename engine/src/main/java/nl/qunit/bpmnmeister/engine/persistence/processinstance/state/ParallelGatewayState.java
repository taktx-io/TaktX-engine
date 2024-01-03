package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class ParallelGatewayState extends BpmnElementState {
  Set<String> triggeredFlows;

  @BsonCreator
  public ParallelGatewayState(
      @BsonProperty("state") StateEnum state,
      @BsonProperty("triggeredFlows") Set<String> triggeredFlows) {
    super(state);
    this.triggeredFlows = triggeredFlows;
  }
}
