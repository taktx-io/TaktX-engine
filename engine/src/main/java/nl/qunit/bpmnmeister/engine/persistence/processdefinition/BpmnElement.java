package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.io.Serializable;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Data
@EqualsAndHashCode
public class BpmnElement implements Serializable {
  private String id;
  private Set<String> outputFlows;

  protected BpmnElement() {}

  protected BpmnElement(String id, Set<String> outputFlows) {
    this.id = id;
    this.outputFlows = outputFlows;
  }

  public BpmnElementState createState() {
    return null;
  }
}
