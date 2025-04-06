package io.taktx.xml;

import io.taktx.bpmn.TDefinitions;
import io.taktx.dto.v_1_0_0.ParsedDefinitionsDTO;

public interface BpmnMapper {

  ParsedDefinitionsDTO map(TDefinitions definitions, String hash);
}
