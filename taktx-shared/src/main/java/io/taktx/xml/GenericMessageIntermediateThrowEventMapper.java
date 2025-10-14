package io.taktx.xml;

import io.taktx.bpmn.TIntermediateThrowEvent;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.MessageIntermediateThrowEventDTO;

public class GenericMessageIntermediateThrowEventMapper
    implements MessageIntermediateThrowEventMapper {
  @Override
  public MessageIntermediateThrowEventDTO map(
      TIntermediateThrowEvent endEvent, String parentId, InputOutputMappingDTO ioMapping) {
    throw new UnsupportedOperationException(
        "GenericMessageIntermediateThrowEventMapper does not support mapping for tIntermediateThrowEvent with message definition: "
            + endEvent);
  }
}
