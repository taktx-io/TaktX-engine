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
public class StartEventState extends BpmnElementState {
  private final Set<String> activeTimerIds;

  @BsonCreator
  public StartEventState(
      @BsonProperty("state") StateEnum state,
      @BsonProperty("timerIds") Set<String> activeTimerIds) {
    super(state);
    this.activeTimerIds = activeTimerIds;
  }
}
