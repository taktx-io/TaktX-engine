package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TBaseElement;
import com.flomaestro.takt.dto.v_1_0_0.InputOutputMappingDTO;
import java.util.Set;

public class GenericIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMappingDTO map(TBaseElement tCatchEvent) {

    // TODO Implement generic io mapping
    return new InputOutputMappingDTO(Set.of(), Set.of());
  }
}
