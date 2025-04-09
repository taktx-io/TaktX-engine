package io.taktx.xml;

import io.taktx.bpmn.TDefinitions;
import io.taktx.dto.ParsedDefinitionsDTO;

public interface BpmnMapper {

  ParsedDefinitionsDTO map(TDefinitions definitions, String hash);
}
