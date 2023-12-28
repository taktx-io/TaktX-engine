package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@SuperBuilder
@Getter
@BsonDiscriminator
public abstract class FlowNode extends FlowElement {
  private Set<String> incoming;
  private Set<String> outgoing;

  protected FlowNode(
      @BsonProperty("id") String id,
      @BsonProperty("incoming") Set<String> incoming,
      @BsonProperty("outgoing") Set<String> outgoing) {
    super(id);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
