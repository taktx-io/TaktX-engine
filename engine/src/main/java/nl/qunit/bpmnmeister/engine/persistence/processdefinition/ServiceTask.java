package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.ServiceTaskState;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.StateEnum;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Data
@EqualsAndHashCode(callSuper = true)
@BsonDiscriminator
public class ServiceTask extends BpmnElement {
  public ServiceTask() {
    super();
  }

  public ServiceTask(String id, Set<String> outputFlows) {
    super(id, outputFlows);
  }

  @Override
  public BpmnElementState createState() {
    return new ServiceTaskState();
  }
}
