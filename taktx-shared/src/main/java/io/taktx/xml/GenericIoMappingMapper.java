package io.taktx.xml;

import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.v_1_0_0.InputOutputMappingDTO;
import java.util.Set;

public class GenericIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMappingDTO map(TBaseElement tCatchEvent) {

    // TODO Implement generic io mapping
    return new InputOutputMappingDTO(Set.of(), Set.of());
  }
}
