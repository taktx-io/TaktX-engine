package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TDefinitions;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;

public interface BpmnMapper {

  ParsedDefinitionsDTO map(TDefinitions definitions, String hash);
}
