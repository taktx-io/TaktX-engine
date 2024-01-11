package nl.qunit.bpmnmeister.pd.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.RootElement;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnMapper {
  private final RootElementMapper rootElementMapper;

  public Definitions map(TDefinitions definitions, String hash) {
    String id = "unknown";
    Map<String, BaseElement> elements = new HashMap<>();
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      RootElement rootElement = rootElementMapper.map(tRootElement);
      if (rootElement != null) {
        id = rootElement.getId();
        elements.put(id, rootElement);
      }
    }

    return Definitions.builder().processDefinitionId(id).hash(hash).elements(elements).build();
  }
}
