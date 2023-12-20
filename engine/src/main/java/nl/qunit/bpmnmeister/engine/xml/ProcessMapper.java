package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;

@ApplicationScoped
public class ProcessMapper {
  public BpmnElement map(TProcess process) {
    return null;
  }
}
