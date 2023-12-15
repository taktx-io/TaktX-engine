package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Set;
import nl.qunit.bpmnmeister.model.processinstance.BpmnElementState;

public interface BpmnElement {
  String id();

  Set<String> outputFlows();

  BpmnElementState createState();
}
