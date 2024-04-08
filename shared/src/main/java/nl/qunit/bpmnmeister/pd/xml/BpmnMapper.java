package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.DefinitionsKey;
import nl.qunit.bpmnmeister.pd.model.Process;

public class BpmnMapper {
  private BpmnMapper() {}

  public static Definitions map(TDefinitions definitions, String hash) {
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      Process rootElement = RootElementMapper.map(tRootElement);
      return new Definitions(new DefinitionsKey(rootElement.getId(), hash), rootElement);
    }

    return Definitions.NONE;
  }
}
