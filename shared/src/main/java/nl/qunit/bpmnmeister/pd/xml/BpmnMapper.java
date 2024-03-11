package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.Map;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.model.FlowElements;
import nl.qunit.bpmnmeister.pd.model.Process;
import nl.qunit.bpmnmeister.pd.model.RootElement;

public class BpmnMapper {
  private BpmnMapper() {}

  public static Definitions map(TDefinitions definitions, String hash, Integer generation) {
    BaseElementId id = BaseElementId.NONE;
    Map<BaseElementId, BaseElement> elements = new HashMap<>();
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      Process rootElement = RootElementMapper.map(tRootElement);
      return new Definitions(id, generation, hash, rootElement);
    }

    return Definitions.NONE;
  }
}
