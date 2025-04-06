package io.taktx.xml;

import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;

public interface IoMappingMapper {
  InputOutputMappingDTO map(TBaseElement tCatchEvent);
}
