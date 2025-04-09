package io.taktx.xml;

import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.InputOutputMappingDTO;

public interface IoMappingMapper {
  InputOutputMappingDTO map(TBaseElement tCatchEvent);
}
