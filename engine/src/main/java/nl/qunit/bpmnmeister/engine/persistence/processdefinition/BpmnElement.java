package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.BpmnElementState;

public interface BpmnElement {
  String id();

  Set<String> outputFlows();

  BpmnElementState createState();
}
