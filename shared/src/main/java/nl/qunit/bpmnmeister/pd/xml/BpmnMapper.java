package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.Definitions;

public interface BpmnMapper {
  Definitions map(TDefinitions definitions, String hash);
}
