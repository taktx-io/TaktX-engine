package nl.qunit.bpmnmeister.pd.xml;

import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.TBaseElement;
import nl.qunit.bpmnmeister.pd.model.InputOutputMapping;

public class GenericIoMappingMapper implements IoMappingMapper {

  @Override
  public InputOutputMapping map(TBaseElement tCatchEvent) {

    // TODO Implement generic io mapping
    return new InputOutputMapping(Set.of(), Set.of());
  }
}
