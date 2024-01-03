package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@Getter
@BsonDiscriminator
public abstract class FlowElement extends BaseElement {
  protected FlowElement(String id) {
    super(id);
  }
}
