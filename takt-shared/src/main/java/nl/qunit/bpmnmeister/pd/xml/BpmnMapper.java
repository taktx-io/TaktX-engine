package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.ParsedDefinitionsDTO;

public interface BpmnMapper {

  ParsedDefinitionsDTO map(TDefinitions definitions, String hash);
}
