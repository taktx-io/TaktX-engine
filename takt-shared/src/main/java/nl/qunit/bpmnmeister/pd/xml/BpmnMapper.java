package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParsedDefinitionsDTO;

public interface BpmnMapper {

  ParsedDefinitionsDTO map(TDefinitions definitions, String hash);
}
