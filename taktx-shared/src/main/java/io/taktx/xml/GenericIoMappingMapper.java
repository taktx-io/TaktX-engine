package io.taktx.xml;

import io.taktx.bpmn.TBaseElement;
import io.taktx.dto.InputOutputMappingDTO;
import java.util.Set;

public class GenericIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMappingDTO map(TBaseElement tCatchEvent) {

    // TODO Implement generic io mapping
    return new InputOutputMappingDTO(Set.of(), Set.of());
  }
}
