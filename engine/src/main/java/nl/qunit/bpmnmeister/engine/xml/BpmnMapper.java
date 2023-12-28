package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import java.util.*;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.*;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.*;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnMapper {
  private final RootElementMapper rootElementMapper;

  public Definitions map(TDefinitions definitions) {
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

    return Definitions.builder().processDefinitionId(id).elements(elements).build();
  }
}
