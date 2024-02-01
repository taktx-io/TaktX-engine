package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.RootElement;

public class BpmnMapper {

  public static Definitions map(TDefinitions definitions, String hash, Integer generation) {
    String id = "unknown";
    Map<String, BaseElement> elements = new HashMap<>();
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      RootElement rootElement = RootElementMapper.map(tRootElement);
      if (rootElement != null) {
        id = rootElement.getId();
        elements.put(id, rootElement);
      }
    }

    return Definitions.builder()
        .processDefinitionId(id)
        .generation(generation)
        .hash(hash)
        .elements(elements)
        .build();
  }
}
