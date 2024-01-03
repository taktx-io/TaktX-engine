package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class StartEvent extends CatchEvent {

  @BsonCreator
  public StartEvent(
      @BsonId String id,
      @BsonProperty("incoming") Set<String> incoming,
      @BsonProperty("outgoing") Set<String> outgoing,
      @BsonProperty("eventDefinitions") Set<EventDefinition> eventDefinitions) {
    super(eventDefinitions, id, incoming, outgoing);
  }
}
