package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import nl.qunit.bpmnmeister.bpmn.TEventDefinition;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EventDefinitionDTO;

public interface EventDefinitionMapper {
  Set<EventDefinitionDTO> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId);
}
