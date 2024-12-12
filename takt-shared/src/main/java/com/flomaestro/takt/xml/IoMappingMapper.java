package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TBaseElement;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;

public interface IoMappingMapper {
  InputOutputMappingDTO map(TBaseElement tCatchEvent);
}
