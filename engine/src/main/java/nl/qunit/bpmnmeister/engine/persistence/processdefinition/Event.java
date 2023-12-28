package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@BsonDiscriminator
@Getter
public abstract class Event extends FlowNode {
  protected Event(String id, Set<String> incoming, Set<String> outgoing) {
    super(id, incoming, outgoing);
  }
}
