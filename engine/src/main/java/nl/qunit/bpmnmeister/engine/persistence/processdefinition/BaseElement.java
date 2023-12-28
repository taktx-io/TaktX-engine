package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@Getter
@BsonDiscriminator
public abstract class BaseElement {
  private final String id;

  protected BaseElement(String id) {
    this.id = id;
  }
}
