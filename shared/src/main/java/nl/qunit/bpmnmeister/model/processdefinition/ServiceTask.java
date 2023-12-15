package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;
import nl.qunit.bpmnmeister.model.processinstance.ServiceTaskState;
import nl.qunit.bpmnmeister.model.processinstance.ServiceTaskStateEnum;

public record ServiceTask(String id, Set<String> outputFlows) implements BpmnElement {
  @Override
  public BpmnElementState createState() {
    return new ServiceTaskState(ServiceTaskStateEnum.INIT);
  }
}
