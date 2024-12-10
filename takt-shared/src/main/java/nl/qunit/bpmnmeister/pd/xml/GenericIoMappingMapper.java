package nl.qunit.bpmnmeister.pd.xml;

import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.InputOutputMappingDTO;

public class GenericIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMappingDTO map(TBaseElement tCatchEvent) {

    // TODO Implement generic io mapping
    return new InputOutputMappingDTO(Set.of(), Set.of());
  }
}
