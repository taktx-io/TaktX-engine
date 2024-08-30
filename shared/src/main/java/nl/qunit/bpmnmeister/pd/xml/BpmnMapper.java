package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;

public interface BpmnMapper {
  DefinitionsDTO map(TDefinitions definitions, String hash);
}
