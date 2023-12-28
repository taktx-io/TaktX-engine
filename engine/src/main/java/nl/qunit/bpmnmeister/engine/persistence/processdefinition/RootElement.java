package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@BsonDiscriminator
@Getter
public abstract class RootElement extends BaseElement {
  protected RootElement(String id) {
    super(id);
  }
}
