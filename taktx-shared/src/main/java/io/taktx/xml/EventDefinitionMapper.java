package io.taktx.xml;

import io.taktx.bpmn.TEventDefinition;
import io.taktx.dto.v_1_0_0.EventDefinitionDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;

public interface EventDefinitionMapper {
  Set<EventDefinitionDTO> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId);
}
