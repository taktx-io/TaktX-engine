package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@SuperBuilder
@BsonDiscriminator
@Getter
public abstract class CatchEvent extends Event {
  protected final Set<EventDefinition> eventDefinitions;

  protected CatchEvent(
      Set<EventDefinition> eventDefinitions,
      String id,
      Set<String> incoming,
      Set<String> outgoing) {
    super(id, incoming, outgoing);
    this.eventDefinitions = eventDefinitions;
  }

  @BsonIgnore
  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }
}
